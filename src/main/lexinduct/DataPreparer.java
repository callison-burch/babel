package main.lexinduct;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import babel.content.corpora.accessors.CorpusAccessor;
import babel.content.corpora.accessors.CrawlCorpusAccessor;
import babel.content.corpora.accessors.EuroParlCorpusAccessor;
import babel.content.corpora.accessors.LexCorpusAccessor;
import babel.content.corpora.accessors.WikiTempCorpusAccessor;
import babel.content.eqclasses.EquivalenceClass;
import babel.content.eqclasses.SimpleEquivalenceClass;
import babel.content.eqclasses.collectors.EquivalenceClassCollector;
import babel.content.eqclasses.collectors.SimpleEquivalenceClassCollector;
import babel.content.eqclasses.comparators.NumberComparator;
import babel.content.eqclasses.filters.DictionaryFilter;
import babel.content.eqclasses.filters.EquivalenceClassFilter;
import babel.content.eqclasses.filters.GarbageFilter;
import babel.content.eqclasses.filters.LengthFilter;
import babel.content.eqclasses.filters.NoContextFilter;
import babel.content.eqclasses.filters.NoTimeDistributionFilter;
import babel.content.eqclasses.filters.NumOccurencesFilter;
import babel.content.eqclasses.filters.RomanizationFilter;
import babel.content.eqclasses.filters.StopWordsFilter;
import babel.content.eqclasses.properties.context.Context;
import babel.content.eqclasses.properties.context.ContextCollector;
import babel.content.eqclasses.properties.number.Number;
import babel.content.eqclasses.properties.number.NumberCollector;
import babel.content.eqclasses.properties.time.TimeDistribution;
import babel.content.eqclasses.properties.time.TimeDistributionCollector;
import babel.content.eqclasses.properties.type.Type;
import babel.content.eqclasses.properties.type.Type.EqType;

import babel.ranking.scorers.Scorer;
import babel.util.config.Configurator;
import babel.util.dict.Dictionary;
import babel.util.dict.ProbabilisticDictionary;
import babel.util.dict.SimpleDictionary;
import babel.util.dict.SimpleDictionary.DictHalves;
import babel.util.persistence.EqClassPersister;

public class DataPreparer
{
  protected static final Log LOG = LogFactory.getLog(DataPreparer.class);
  
  protected static final String CONTEXT_SRC_MAP_FILE = "cont.src.map";
  protected static final String CONTEXT_TRG_MAP_FILE = "cont.trg.map";

  protected static final String CONTEXT_SRC_PROP_EXT = ".cont.src.map";
  protected static final String CONTEXT_TRG_PROP_EXT = ".cont.trg.map";
  
  protected static final String SRC_MAP_FILE = "src.map";
  protected static final String TRG_MAP_FILE = "trg.map";
  
  protected static final String SRC_PROP_EXT = ".src.map";
  protected static final String TRG_PROP_EXT = ".trg.map";

  protected static final String SRC_TO_INDUCT = "srcinduct.list";
  
  @SuppressWarnings("unchecked")
  public void prepare() throws Exception
  {
    boolean filterRomanSrc = Configurator.CONFIG.containsKey("preprocessing.FilterRomanSrc") && Configurator.CONFIG.getBoolean("preprocessing.FilterRomanSrc");
    boolean filterRomanTrg = Configurator.CONFIG.containsKey("preprocessing.FilterRomanTrg") && Configurator.CONFIG.getBoolean("preprocessing.FilterRomanTrg");
    String srcEqClassName = Configurator.CONFIG.getString("preprocessing.candidates.SrcEqClass");
    String trgEqClassName = Configurator.CONFIG.getString("preprocessing.candidates.TrgEqClass");
    String srcContEqClassName = Configurator.CONFIG.getString("preprocessing.context.SrcEqClass");
    String trgContEqClassName = Configurator.CONFIG.getString("preprocessing.context.TrgEqClass");
    boolean alignDistros = Configurator.CONFIG.getBoolean("preprocessing.time.Align");
    String srcStopFileName = Configurator.CONFIG.containsKey("resources.stopwords.SrcStopWords") ? Configurator.CONFIG.getString("resources.stopwords.SrcStopWords") : null;
    String trgStopFileName = Configurator.CONFIG.containsKey("resources.stopwords.TrgStopWords") ? Configurator.CONFIG.getString("resources.stopwords.TrgStopWords") : null;
    boolean filterGarbage = Configurator.CONFIG.containsKey("preprocessing.FilterGarbage") ? Configurator.CONFIG.getBoolean("preprocessing.FilterGarbage") : true; 
    
    Class<EquivalenceClass> srcEqClassClass = (Class<EquivalenceClass>)Class.forName(srcEqClassName);
    Class<EquivalenceClass> trgEqClassClass = (Class<EquivalenceClass>)Class.forName(trgEqClassName);
    Class<EquivalenceClass> srcContClassClass = (Class<EquivalenceClass>)Class.forName(srcContEqClassName);
    Class<EquivalenceClass> trgContClassClass = (Class<EquivalenceClass>)Class.forName(trgContEqClassName);
    
    // Prepare equivalence classes and their properties
    try
    {
      LOG.info(" - Reading context source classes from " + CONTEXT_SRC_MAP_FILE + " and target from " + CONTEXT_TRG_MAP_FILE + "...");
      m_contextSrcEqs = readEqClasses(true, srcContClassClass, CONTEXT_SRC_MAP_FILE, CONTEXT_SRC_PROP_EXT);
      m_contextTrgEqs = readEqClasses(false, trgContClassClass, CONTEXT_TRG_MAP_FILE, CONTEXT_TRG_PROP_EXT);
      LOG.info(" - Context source classes: " + m_contextSrcEqs.size());
      LOG.info(" - Context target classes: " + m_contextTrgEqs.size());
  
      LOG.info(" - Reading candidate source classes from " + SRC_MAP_FILE + " and target from " + TRG_MAP_FILE + "...");
      m_srcEqs = readEqClasses(true, srcEqClassClass, SRC_MAP_FILE, SRC_PROP_EXT);
      m_trgEqs = readEqClasses(false, trgEqClassClass, TRG_MAP_FILE, TRG_PROP_EXT);
      LOG.info(" - Candidate source classes: " + m_srcEqs.size());
      LOG.info(" - Candidate target classes: " + m_trgEqs.size());
      
      prepareDictsAndSrcEqsToInduct(m_contextSrcEqs, m_contextTrgEqs, m_srcEqs, m_trgEqs);
      prepareTranslitDictionary(m_contextSrcEqs);
      //Prepare dictionary for PROJECTION
      prepareProjDictionary(m_contextSrcEqs, m_contextTrgEqs);
      // If probabilistic dictionary, prepare it now
      if (Configurator.CONFIG.containsKey("resources.probprojdictionary.Path")){
		  setProbProjDict(m_contextSrcEqs, m_contextTrgEqs);    	  
      }
      
    		  
      
      LOG.info(" - Reading source and target properties...");
      readProps(true, m_srcEqs, SRC_PROP_EXT);
      readProps(false, m_trgEqs, TRG_PROP_EXT);
    }
    catch(Exception e)
    { 
      LOG.info(" - Failed to read previously collected stuff (" + e.toString() + "), collecting from scratch ...");
      
      
      Set<EquivalenceClass> allSrcEqs = collectInitEqClasses(true, filterRomanSrc, filterGarbage);
      Set<EquivalenceClass> allTrgEqs = collectInitEqClasses(false, filterRomanTrg, filterGarbage);
      LOG.info(" - All source types: " + allSrcEqs.size() + (filterRomanSrc ? " (without romanization) " : ""));
      LOG.info(" - All target types: " + allTrgEqs.size() + (filterRomanTrg ? " (without romanization) " : ""));
      
      LOG.info(" - Constructing context classes...");
      m_contextSrcEqs = constructEqClasses(true, allSrcEqs, srcContClassClass);
      m_contextTrgEqs = constructEqClasses(false, allTrgEqs, trgContClassClass);     
      LOG.info(" - Context source classes: " + m_contextSrcEqs.size());
      LOG.info(" - Context target classes: " + m_contextTrgEqs.size());
      
      LOG.info(" - Writing context classes...");
      writeEqs(m_contextSrcEqs, true, CONTEXT_SRC_MAP_FILE, CONTEXT_SRC_PROP_EXT);
      writeEqs(m_contextTrgEqs, false, CONTEXT_TRG_MAP_FILE, CONTEXT_TRG_PROP_EXT);
      
      LOG.info(" - Constructing candidate classes...");
      m_srcEqs = constructEqClasses(true, allSrcEqs, srcEqClassClass);
      m_trgEqs = constructEqClasses(false, allTrgEqs, trgEqClassClass);
      LOG.info(" - Candidate source classes: " + m_srcEqs.size());
      LOG.info(" - Candidate target classes: " + m_trgEqs.size());

      LOG.info(" - Pruning candidate classes...");
      m_srcEqs = pruneEqClasses(m_srcEqs, true, srcStopFileName, filterRomanSrc, filterGarbage);
      m_trgEqs = pruneEqClasses(m_trgEqs, false, trgStopFileName, filterRomanTrg, filterGarbage);
      LOG.info(" - Pruned candidate source classes: " + m_srcEqs.size());
      LOG.info(" - Pruned candidate target classes: " + m_trgEqs.size());
      
      //m_contextSrcEqs and m_contextTrgEqs for seed dictionary
      //m_srcEqs and m_trgEqs for test dictionary
      prepareDictsAndSrcEqsToInduct(m_contextSrcEqs, m_contextTrgEqs, m_srcEqs, m_trgEqs);
      prepareTranslitDictionary(m_contextSrcEqs);
      //Prepare dictionary for PROJECTION
      prepareProjDictionary(m_contextSrcEqs, m_contextTrgEqs);
      // If probabilistic dictionary, prepare it now
      if (Configurator.CONFIG.containsKey("resources.probprojdictionary.Path")){
		  setProbProjDict(m_contextSrcEqs, m_contextTrgEqs);    	  
      }      
      
      LOG.info(" - Collecting candidate properties...");
      Set<Integer> srcBins = collectProps(true, m_srcEqs, m_contextSrcEqs, m_projDict);
      Set<Integer> trgBins = collectProps(false, m_trgEqs, m_contextTrgEqs, m_projDict);

      if (alignDistros)
      {
        LOG.info(" - Aligning temporal distributions...");
        alignDistributions(srcBins, trgBins, m_srcEqs, m_trgEqs);
      }
      
      LOG.info(" - Cleaning up candidate classes...");
      m_srcEqs = cleanUpEqClasses(m_srcEqs, true);
      m_trgEqs = cleanUpEqClasses(m_trgEqs, false); 
      LOG.info(" - Candidate source classes: " + m_srcEqs.size());
      LOG.info(" - Candidate target classes: " + m_trgEqs.size());
      
      LOG.info(" - Writing candidate classes and properties...");
      writeEqs(m_srcEqs, true, SRC_MAP_FILE, SRC_PROP_EXT);
      writeProps(m_srcEqs, true, SRC_PROP_EXT);
      writeEqs(m_trgEqs, false, TRG_MAP_FILE, TRG_PROP_EXT);
      writeProps(m_trgEqs, false, TRG_PROP_EXT);
    }
    
    // Measure dictionary coverage
    dictCoverage(m_seedDict, m_contextSrcEqs, true);
    dictCoverage(m_seedDict, m_contextTrgEqs, false);
   
    collectTokenCounts(m_contextSrcEqs, m_contextTrgEqs); 
  }


  public void prepareProperties(boolean src, Set<? extends EquivalenceClass> eqs, Scorer contextScorer, Scorer timeScorer)
  {
    LOG.info("Projecting and scoring " + (src ? "source" : "target") + " contextual items with " + contextScorer.toString() + " and time distributions with " + timeScorer.toString() + "...");

    for (EquivalenceClass eq : eqs)
    { 
      contextScorer.prepare(eq);
      timeScorer.prepare(eq);
    }
  }
  
  public Dictionary getSeedDict()
  { return m_seedDict;
  }
    
  public Set<EquivalenceClass> getSrcEqs()
  { return m_srcEqs;
  }

  public Set<EquivalenceClass> getSrcEqsToInduct()
  { return m_srcEqsToInduct;
  }
  
  public Set<EquivalenceClass> getTrgEqs()
  { return m_trgEqs;
  }
  
  public double getNumSrcToks()
  { return m_numToksInSrc;
  }
  
  public double getNumTrgToks()
  { return m_numToksInTrg;
  }
  
  public double getMaxSrcTokCount()
  { return m_maxTokCountInSrc;
  }
  
  public double getMaxTrgTokCount()
  { return m_maxTokCountInTrg; 
  }
  
  public SimpleDictionary getTranslitDict() {
	    return m_translitDict;
	  }

  public Dictionary getProjDict() {
	    return m_projDict;
	  }
  
  
  public ProbabilisticDictionary getProbProjDict(){
	  return m_probProjDict;
  }

  public void setProbProjDict(Set<EquivalenceClass> srcEqs, Set<EquivalenceClass> trgEqs) throws Exception{
	    String dictDir = Configurator.CONFIG.getString("resources.probprojdictionary.Path");
	    int ridDictNumTrans = Configurator.CONFIG.containsKey("experiments.ProjDictionaryPruneNumTranslations") ? Configurator.CONFIG.getInt("experiments.DictionaryPruneNumTranslations") : -1;
	    
	    LOG.info(" - Reading/preparing probabilistic projection dictionary ...");
	    
	    String dictFileName = Configurator.CONFIG.getString("resources.probprojdictionary.Dictionary");
	    m_probProjDict = new ProbabilisticDictionary(dictDir + dictFileName, "ProbabilisticProjectionDictionary", srcEqs, trgEqs);

	    m_probProjDict.pruneCounts(ridDictNumTrans);
	    	    
	    LOG.info(" - Probabilistic Projection dictionary: " + m_probProjDict.toString()); 
  }
  
  
  protected Set<EquivalenceClass> readEqClasses(boolean src, Class<? extends EquivalenceClass> eqClsssClass, String eqfileName, String propFileExtension) throws Exception
  {
    // Read init classes
    String preProcDir = Configurator.CONFIG.getString("preprocessing.Path");        
    Set<EquivalenceClass> eqClasses = EqClassPersister.unpersistEqClasses(eqClsssClass, preProcDir + eqfileName);
    
    // Read counts property
    EqClassPersister.unpersistProperty(eqClasses, Number.class.getName(), preProcDir + Number.class.getSimpleName() + propFileExtension);
    
    // Assign type property
    assignTypeProp(eqClasses, src ? EqType.SOURCE : EqType.TARGET);
    
    return eqClasses;
  }
  
  protected Set<EquivalenceClass> collectInitEqClasses(boolean src, boolean filterRoman, boolean filterGarbage) throws Exception
  {
    Set<EquivalenceClass> eqClasses;
  
    ArrayList<EquivalenceClassFilter> filters = new ArrayList<EquivalenceClassFilter>(3);
    if (filterGarbage){
      filters.add(new GarbageFilter());
      filters.add(new LengthFilter(2));
      LOG.info("Filtering garbarge and by length");
    }
    else{
    	LOG.info("WARNING: not filtering garbage");
    }
    if (filterRoman)
    { filters.add(new RomanizationFilter());
    	LOG.info("Filtering roman characters");
    }
    else{
    	LOG.info("Not filtering roman characters");
    }
    
    CorpusAccessor accessor = getAccessor(Configurator.CONFIG.getString("preprocessing.input.Context"), src);
    if (src){
    	//accessor.
    }
    
    // Collect init classes
    SimpleEquivalenceClassCollector collector = new SimpleEquivalenceClassCollector(filters, false);
    eqClasses = collector.collect(accessor.getCorpusReader(), -1);

    // Collect counts property
    (new NumberCollector(false)).collectProperty(accessor, eqClasses);

    // Assign type property
    assignTypeProp(eqClasses, src ? EqType.SOURCE : EqType.TARGET);
    
    return eqClasses;
  }

  protected void prepareTranslitDictionary(Set<EquivalenceClass> srcContEqs) throws Exception {
      
	    LOG.info(" - Reading/preparing transliteration dictionary ...");
	    
	    String dictDir = Configurator.CONFIG.containsKey("resources.translit.Path") ? Configurator.CONFIG.getString("resources.translit.Path") : null;

	    if ((dictDir == null) || (dictDir.trim().length() == 0)) {
	      
	      LOG.info(" - No transliteration dictionary specified");
	      
	    } else {
	      
	      if (Configurator.CONFIG.containsKey("resources.translit.Dictionary")) {
	        String dictFileName = Configurator.CONFIG.getString("resources.translit.Dictionary");
	        m_translitDict = new SimpleDictionary(dictDir + dictFileName, "Translit");
	      } else {
	        String srcDictFileName = Configurator.CONFIG.getString("resources.translit.SrcName");
	        String trgDictFileName = Configurator.CONFIG.getString("resources.translit.TrgName");      
	        m_translitDict = new SimpleDictionary(new DictHalves(dictDir + srcDictFileName, dictDir + trgDictFileName) , "TranslitDictionary");
	      }
	        
	      LOG.info(" - Transliteration dictionary: " + m_translitDict.toString()); 
	    }
	  }
  
  
  /***
   * Given set of src Cont eqs and trg Cont eqs that appear in monolingual data, save a dictionary that contains translations between the two
   * @param srcContEqs
   * @param trgContEqs
   * @throws Exception
   */
  protected void prepareProjDictionary(Set<EquivalenceClass> srcContEqs, Set<EquivalenceClass> trgContEqs) throws Exception {
	    
	    String dictDir = Configurator.CONFIG.getString("resources.projdictionary.Path");
	    int ridDictNumTrans = Configurator.CONFIG.containsKey("experiments.ProjDictionaryPruneNumTranslations") ? Configurator.CONFIG.getInt("experiments.DictionaryPruneNumTranslations") : -1;
	    SimpleDictionary simpProjDict;
	    
	    LOG.info(" - Reading/preparing projection dictionary ...");
	    
	    if (Configurator.CONFIG.containsKey("resources.projdictionary.Dictionary")) {
	      String dictFileName = Configurator.CONFIG.getString("resources.projdictionary.Dictionary");
	      simpProjDict = new SimpleDictionary(dictDir + dictFileName, "ProjectionDictionary");
	    } else {
	      String srcDictFileName = Configurator.CONFIG.getString("resources.projdictionary.SrcName");
	      String trgDictFileName = Configurator.CONFIG.getString("resources.projdictionary.TrgName");      
	      simpProjDict = new SimpleDictionary(new DictHalves(dictDir + srcDictFileName, dictDir + trgDictFileName) , "ProjectionDictionary");
	    }

	    simpProjDict.pruneCounts(ridDictNumTrans);
	    
	    m_projDict = new Dictionary(srcContEqs, trgContEqs, simpProjDict, "ProjDictionary");
	    
	    LOG.info(" - Projection dictionary: " + m_projDict.toString()); 
	  }
  
  protected Set<EquivalenceClass> constructEqClasses(boolean src, Set<EquivalenceClass> allEqs, Class<? extends EquivalenceClass> eqClassClass) throws Exception
  {    
    HashMap<String, EquivalenceClass> eqsMap = new HashMap<String, EquivalenceClass>();
    EquivalenceClass newEq, foundEq;
    String newWord;
    long newCount;
    
    for (EquivalenceClass eq : allEqs)
    {
      newWord = ((SimpleEquivalenceClass)eq).getWord(); // TODO: not pretty
      newCount = ((Number)eq.getProperty(Number.class.getName())).getNumber();
      
      newEq = eqClassClass.newInstance();
      newEq.init(newWord, false);
      
      if (null == (foundEq = eqsMap.get(newEq.getStem())))
      {
        newEq.assignId();
        
        newEq.setProperty(new Number(newCount));
        newEq.setProperty(new Type(src ? EqType.SOURCE : EqType.TARGET));
        
        eqsMap.put(newEq.getStem(), newEq);
      }
      else
      {
        foundEq.merge(newEq);
 
        ((Number)foundEq.getProperty(Number.class.getName())).increment(newCount);
      }
    }
    
    return new HashSet<EquivalenceClass>(eqsMap.values());
  }
  
  protected void readProps(boolean src, Set<EquivalenceClass> eqClasses, String propFileExtension) throws Exception
  {
    String preProcDir = Configurator.CONFIG.getString("preprocessing.Path");
    
    // Read properties
    EqClassPersister.unpersistProperty(eqClasses, Number.class.getName(), preProcDir + Number.class.getSimpleName() + propFileExtension); 
    EqClassPersister.unpersistProperty(eqClasses, Context.class.getName(), preProcDir + Context.class.getSimpleName() + propFileExtension);  
    EqClassPersister.unpersistProperty(eqClasses, TimeDistribution.class.getName(), preProcDir + TimeDistribution.class.getSimpleName() + propFileExtension);
    
    // Assign type property
    assignTypeProp(eqClasses, src ? EqType.SOURCE : EqType.TARGET);
  }
  
  protected Set<Integer> collectProps(boolean src, Set<EquivalenceClass> eqClasses, Set<EquivalenceClass> contextEqs, Dictionary contextDict) throws Exception
  {
    int pruneContEqIfOccursFewerThan = Configurator.CONFIG.getInt("preprocessing.context.PruneEqIfOccursFewerThan");
    int pruneContEqIfOccursMoreThan = Configurator.CONFIG.getInt("preprocessing.context.PruneEqIfOccursMoreThan");
    int contextWindowSize = Configurator.CONFIG.getInt("preprocessing.context.Window");
    
    Set<EquivalenceClass> filtContextEqs = new HashSet<EquivalenceClass>(contextEqs);

    LOG.info("Preparing contextual words for " + (src ? "source" : "target") + ": keeping those in dict [" + contextDict.toString() + "] and occuring (" + pruneContEqIfOccursFewerThan + "," + pruneContEqIfOccursMoreThan + ") times...");
    LinkedList<EquivalenceClassFilter> filters = new LinkedList<EquivalenceClassFilter>();
    filters.add(new DictionaryFilter(contextDict, true, src)); 
    filters.add(new NumOccurencesFilter(pruneContEqIfOccursFewerThan, true));
    filters.add(new NumOccurencesFilter(pruneContEqIfOccursMoreThan, false));
    filtContextEqs = EquivalenceClassCollector.filter(filtContextEqs, filters);
    LOG.info("Context " + (src ? "source" : "target") + " classes: " + filtContextEqs.size());
    
    // Collect properties
    CorpusAccessor accessor = getAccessor(Configurator.CONFIG.getString("preprocessing.input.Context"), src);    
    (new ContextCollector(false, false, contextWindowSize, contextWindowSize, filtContextEqs)).collectProperty(accessor, eqClasses);    

    accessor = getAccessor(Configurator.CONFIG.getString("preprocessing.input.Time"), src);
    TimeDistributionCollector distCollector = new TimeDistributionCollector(false);
    distCollector.collectProperty(accessor, eqClasses);
    
    // Assign type property
    assignTypeProp(eqClasses, src ? EqType.SOURCE : EqType.TARGET);
    
    // Returns time bins for which counts were collected
    return distCollector.binsCollected();
  }
  
  //Removes days not in common between src and trg; usually done already in corpora preprocessing
  protected void alignDistributions(Set<Integer> srcBins, Set<Integer> trgBins, Set<EquivalenceClass> srcEqs, Set<EquivalenceClass> trgEqs)
  {
    HashSet<Integer> toRemove = new HashSet<Integer>(srcBins);
    TimeDistribution timeProp;
    toRemove.removeAll(trgBins);
    
    for (EquivalenceClass eq : srcEqs)
    {
      if (null != (timeProp = (TimeDistribution)eq.getProperty(TimeDistribution.class.getName())))
      { timeProp.removeBins(toRemove);
      }
    }
    
    toRemove.clear();
    toRemove.addAll(trgBins);
    toRemove.removeAll(srcBins);
    
    for (EquivalenceClass eq : trgEqs)
    {
      if (null != (timeProp = (TimeDistribution)eq.getProperty(TimeDistribution.class.getName())))
      { timeProp.removeBins(toRemove);
      }
    }       
    
    toRemove.clear();
    toRemove.addAll(srcBins);
    toRemove.retainAll(trgBins);
    
    LOG.info("There are " + srcBins.size() + " days in src distributions."); 
    LOG.info("There are " + trgBins.size() + " days in trg distributions."); 
    LOG.info("There are " + toRemove.size() + " common days between src and trg distributions.");    
  }

  protected Set<EquivalenceClass> pruneEqClasses(Set<EquivalenceClass> eqClasses, boolean src, String stopWordsFileName, boolean filterRoman, boolean filterGarbage) throws Exception
  {
    String stopWordsDir = Configurator.CONFIG.getString("resources.stopwords.Path");
    int pruneCandIfOccursFewerThan = -1;
    if (src) {
    	pruneCandIfOccursFewerThan = Configurator.CONFIG.getInt("preprocessing.candidates.PruneIfOccursFewerThanSrc");
    }
    else{
    	pruneCandIfOccursFewerThan = Configurator.CONFIG.getInt("preprocessing.candidates.PruneIfOccursFewerThanTrg");    	
    }
    int pruneCandIfOccursMoreThan = Configurator.CONFIG.getInt("preprocessing.candidates.PruneIfOccursMoreThan");
    int pruneMostFreq = src ? Configurator.CONFIG.getInt("preprocessing.candidates.PruneMostFrequentSrc") : Configurator.CONFIG.getInt("preprocessing.candidates.PruneMostFrequentTrg");

    LOG.info("Pruning " + (src ? "source" : "target")  + " candidates..."); 
    LOG.info("Pruning candidates if they appear fewer than "+pruneCandIfOccursFewerThan+" times.");
    
    LinkedList<EquivalenceClassFilter> filters = new LinkedList<EquivalenceClassFilter>();
    if (filterGarbage){
    	filters.add(new GarbageFilter());
    }
    if (filterRoman)
    { filters.add(new RomanizationFilter());
    }
    
    if ((stopWordsFileName != null) && (stopWordsFileName.trim().length() > 0))
    {
      SimpleEquivalenceClassCollector collector = new SimpleEquivalenceClassCollector(filters, false);
      Set<? extends EquivalenceClass> stopEqs = ((new File(stopWordsDir + stopWordsFileName)).exists()) ?
          collector.collect((new LexCorpusAccessor(stopWordsFileName, stopWordsDir, true)).getCorpusReader(), -1) :
          new HashSet<EquivalenceClass>();
          
      filters.add(new StopWordsFilter(stopEqs));    
    }
            
    filters.add(new NumOccurencesFilter(pruneCandIfOccursFewerThan, true));
    filters.add(new NumOccurencesFilter(pruneCandIfOccursMoreThan, false));
    
    Set<EquivalenceClass> filteredEqs = EquivalenceClassCollector.filter(eqClasses, filters);
    
    if (pruneMostFreq > 0)
    {
      LOG.info("Removing  " + pruneMostFreq + " most frequent " + (src ? "source" : "target") + " candidates...");
      
      LinkedList<EquivalenceClass> valList = new LinkedList<EquivalenceClass>(filteredEqs);
      Collections.sort(valList, new NumberComparator(false));
      
      for (int i = 0; i < Math.min(pruneMostFreq, valList.size()); i++)
      {
        filteredEqs.remove(valList.get(i));
      }
    }
    
    return filteredEqs;
  }
  
  protected Set<EquivalenceClass> cleanUpEqClasses(Set<EquivalenceClass> eqClasses, boolean src) throws Exception
  {
    LOG.info("Throwing out " + (src ? "source" : "target") + " candidate classes without context or time properties..."); 

    LinkedList<EquivalenceClassFilter> filters = new LinkedList<EquivalenceClassFilter>();
    filters.add(new NoContextFilter());
    filters.add(new NoTimeDistributionFilter());
    
    return EquivalenceClassCollector.filter(eqClasses, filters);    
  }

  protected void writeEqs(Set<EquivalenceClass> eqClasses, boolean src, String eqfileName, String propFileExtension) throws Exception
  {
    String preProcDir = Configurator.CONFIG.getString("preprocessing.Path");

    EqClassPersister.persistEqClasses(eqClasses, preProcDir + eqfileName);        
    EqClassPersister.persistProperty(eqClasses, Number.class.getName(), preProcDir + Number.class.getSimpleName() + propFileExtension);
  }
  
  protected void writeProps(Set<EquivalenceClass> eqClasses, boolean src, String propFileExtension) throws Exception
  {
    String preProcDir = Configurator.CONFIG.getString("preprocessing.Path");

    EqClassPersister.persistProperty(eqClasses, Context.class.getName(), preProcDir + Context.class.getSimpleName() + propFileExtension);
    EqClassPersister.persistProperty(eqClasses, TimeDistribution.class.getName(), preProcDir + TimeDistribution.class.getSimpleName() + propFileExtension);
  }

  protected void dictCoverage(Dictionary dict, Set<EquivalenceClass> eqs, boolean src)
  {
    DecimalFormat df = new DecimalFormat("0.00");
    double tokTotal = 0;
    double tokCovered = 0;
    double typCovered = 0;
    Number numProp;
    double num;
      
    for (EquivalenceClass eq : eqs)
    { 
      if (null != (numProp = ((Number)eq.getProperty(Number.class.getName()))))
      {  
        num = numProp.getNumber();
      
        if ((src && dict.containsSrc(eq)) || (!src && dict.containsTrg(eq)))
        {
          tokCovered += num;
          typCovered++;
        }
        
        tokTotal += num;
      }
    }    
   
    LOG.info("[" + dict.getName() + (src ? "]: source" : "]: target") + " dictionary coverage " + df.format(100.0 * tokCovered / tokTotal) + "% tokens and " + df.format(100.0 * typCovered / (double)eqs.size()) + "% types.");
  }
  
  protected void collectTokenCounts(Set<? extends EquivalenceClass> srcEqs, Set<? extends EquivalenceClass> trgEqs)
  {
    m_maxTokCountInSrc = 0;
    m_maxTokCountInTrg = 0;
    m_numToksInSrc = 0;
    m_numToksInTrg = 0;
    
    Number tmpNum;
    
    for (EquivalenceClass eq : srcEqs)
    {
      if ((tmpNum = (Number)eq.getProperty(Number.class.getName())) != null)
      {
        if (tmpNum.getNumber() > m_maxTokCountInSrc)
        { m_maxTokCountInSrc = tmpNum.getNumber();
        }
        
        m_numToksInSrc += tmpNum.getNumber();
      }
    }

    for (EquivalenceClass eq : trgEqs)
    {
      if ((tmpNum = (Number)eq.getProperty(Number.class.getName())) != null) 
      {
        if (tmpNum.getNumber() > m_maxTokCountInTrg)
        { m_maxTokCountInTrg = tmpNum.getNumber();
        }
        
        m_numToksInTrg += tmpNum.getNumber();
      }
    }
    
    LOG.info("Maximum occurrences: src = " + m_maxTokCountInSrc + ", trg = " + m_maxTokCountInTrg + ".");
    LOG.info("Total Counts: src = " + m_numToksInSrc + ", trg = " + m_numToksInTrg + ".");    
  }

  protected CorpusAccessor getAccessor(String kind, boolean src) throws Exception
  {
    CorpusAccessor accessor = null;

    if ("europarl".equals(kind))
    { accessor = getEuroParlAccessor(src);
    }
    else if ("wiki".equals(kind))
    { accessor = getWikiAccessor(src);
    }
    else if ("wikitemp".equals(kind))
    { accessor = getWikiTempAccessor(src);
    }
    else if ("crawls".equals(kind))
    { accessor = getCrawlsAccessor(src);
    }
    else if ("dev".equals(kind))
    { accessor = getDevAccessor(src);
    }
    else if ("test".equals(kind))
    { accessor = getTestAccessor(src);
    }
    else
    { LOG.error("Could not find corpus accessor for " + kind);
    }
    
    return accessor;
  }

  protected LexCorpusAccessor getDevAccessor(boolean src) throws Exception
  {    
    String path = Configurator.CONFIG.getString("corpora.dev.Path");
    boolean oneSentPerLine = Configurator.CONFIG.getBoolean("corpora.dev.OneSentPerLine");
    String name = src ? Configurator.CONFIG.getString("corpora.dev.SrcName") : Configurator.CONFIG.getString("corpora.dev.TrgName");
        
    return new LexCorpusAccessor(name, appendSep(path), oneSentPerLine);    
  }
  
  protected LexCorpusAccessor getTestAccessor(boolean src) throws Exception
  {    
    String path = Configurator.CONFIG.getString("corpora.test.Path");
    boolean oneSentPerLine = Configurator.CONFIG.getBoolean("corpora.test.OneSentPerLine");
    String name = src ? Configurator.CONFIG.getString("corpora.test.SrcName") : Configurator.CONFIG.getString("corpora.test.TrgName");
        
    return new LexCorpusAccessor(name, appendSep(path), oneSentPerLine);    
  }
  
  protected EuroParlCorpusAccessor getEuroParlAccessor(boolean src) throws Exception
  {    
    String path = Configurator.CONFIG.getString("corpora.europarl.Path");
    boolean oneSentPerLine = Configurator.CONFIG.getBoolean("corpora.europarl.OneSentPerLine");
    String subDir = src ? Configurator.CONFIG.getString("corpora.europarl.SrcSubDir") : Configurator.CONFIG.getString("corpora.europarl.TrgSubDir");

    SimpleDateFormat sdf = new SimpleDateFormat( "yy-MM-dd" );
    Date fromDate = sdf.parse(Configurator.CONFIG.getString("corpora.europarl.DateFrom"));
    Date toDate = sdf.parse(Configurator.CONFIG.getString("corpora.europarl.DateTo"));
    
    return new EuroParlCorpusAccessor(appendSep(path) + subDir, fromDate, toDate, oneSentPerLine);
  }
  
  protected CrawlCorpusAccessor getCrawlsAccessor(boolean src) throws Exception
  {    
    String path = Configurator.CONFIG.getString("corpora.crawls.Path");
    boolean oneSentPerLine = Configurator.CONFIG.getBoolean("corpora.crawls.OneSentPerLine");
    String subDir = src ? Configurator.CONFIG.getString("corpora.crawls.SrcSubDir") : Configurator.CONFIG.getString("corpora.crawls.TrgSubDir");

    SimpleDateFormat sdf = new SimpleDateFormat( "yy-MM-dd" );
    Date fromDate = sdf.parse(Configurator.CONFIG.getString("corpora.crawls.DateFrom"));
    Date toDate = sdf.parse(Configurator.CONFIG.getString("corpora.crawls.DateTo"));
	double corpusSamplingRate = Configurator.CONFIG.getDouble("preprocessing.input.CorpusSampleRate",1.0);		  
	  if (corpusSamplingRate==1.0){
		    return new CrawlCorpusAccessor(appendSep(path) + subDir, fromDate, toDate, oneSentPerLine);
	  }
	  else{
	  	    if (m_corpusTitleFilter==null){
			    LOG.info("Sampling crawls corpus at rate of:"+corpusSamplingRate);
			    CrawlCorpusAccessor myaccessor = new CrawlCorpusAccessor(appendSep(path) + subDir, "UTF-8", fromDate, toDate, oneSentPerLine, corpusSamplingRate);
				m_corpusTitleFilter = myaccessor.getFileList().getNamedFileNames();
				return myaccessor;
		    }
		    else{
		    	LOG.info("Reusing wikitemp corpus sampled file list");
				return new CrawlCorpusAccessor(appendSep(path) + subDir, "UTF-8", fromDate, toDate, oneSentPerLine, m_corpusTitleFilter);		  
		    }
		  }
    //return new CrawlCorpusAccessor(appendSep(path) + subDir, fromDate, toDate, oneSentPerLine);
  }

  
  protected LexCorpusAccessor getWikiAccessor(boolean src)
  {
    String path = Configurator.CONFIG.getString("corpora.wiki.Path");
    boolean oneSentPerLine = Configurator.CONFIG.getBoolean("corpora.wiki.OneSentPerLine");
    String fileRegExp = src ? Configurator.CONFIG.getString("corpora.wiki.SrcRegExp") : Configurator.CONFIG.getString("corpora.wiki.TrgRegExp");
  
    return new LexCorpusAccessor(fileRegExp, appendSep(path), oneSentPerLine);
  }
  
  // If sampling rate specified: Automatically sampling first time (if no sampled file list exists), then using sample throughout
  protected WikiTempCorpusAccessor getWikiTempAccessor(boolean src){
	  String path = Configurator.CONFIG.getString("corpora.wikitemp.Path");
	  boolean oneSentPerLine = Configurator.CONFIG.getBoolean("corpora.wikitemp.OneSentPerLine");
	  String fileRegExp = src ? Configurator.CONFIG.getString("corpora.wikitemp.SrcRegExp") : Configurator.CONFIG.getString("corpora.wikitemp.TrgRegExp");
	  double corpusSamplingRate = Configurator.CONFIG.getDouble("preprocessing.input.CorpusSampleRate",1.0);		  
	  if (corpusSamplingRate==1.0){
		  return new WikiTempCorpusAccessor(fileRegExp, appendSep(path), oneSentPerLine);		  
	  }
	  else{
  	    if (m_corpusTitleFilter==null){
		    LOG.info("Sampling wikitemp corpus at rate of:"+corpusSamplingRate);
			WikiTempCorpusAccessor myaccessor = new WikiTempCorpusAccessor(fileRegExp, appendSep(path), "UTF-8", oneSentPerLine, corpusSamplingRate);
			m_corpusTitleFilter = myaccessor.getFileList().getNamedFileNames();
			return myaccessor;
	    }
	    else{
	    	LOG.info("Reusing wikitemp corpus sampled file list");
			return new WikiTempCorpusAccessor(fileRegExp, appendSep(path), "UTF-8", oneSentPerLine, m_corpusTitleFilter);		  
	    }
	  }
	  //return new WikiTempCorpusAccessor(fileRegExp, appendSep(path), oneSentPerLine);
}  
  
  protected void prepareDictsAndSrcEqsToInduct(
      Set<EquivalenceClass> srcContEqs, Set<EquivalenceClass> trgContEqs,
      Set<EquivalenceClass> srcEqs, Set<EquivalenceClass> trgEqs) throws Exception
  {
    String dictDir = Configurator.CONFIG.getString("resources.dictionary.Path");
    int ridDictNumTrans = Configurator.CONFIG.containsKey("experiments.DictionaryPruneNumTranslations") ? Configurator.CONFIG.getInt("experiments.DictionaryPruneNumTranslations") : -1;
    SimpleDictionary entireDict;
    //boolean allowSeedTestOverlap = Configurator.CONFIG.containsKey("experiments.DictionaryAllowSeedTestOverlap") ? Configurator.CONFIG.getBoolean("experiments.DictionaryAllowSeedTestOverlap") : false;
    //Boolean will check for punctuation and lowercase input test dict. By default, do cleanup.
    boolean testDictCleanup = Configurator.CONFIG.containsKey("experiments.TestDictCleanup") ? Configurator.CONFIG.getBoolean("experiments.TestDictCleanup") : true;
    
    LOG.info("Reading/preparing test dictionaries ...");
    
    if (Configurator.CONFIG.containsKey("resources.dictionary.Dictionary")) {
      String dictFileName = Configurator.CONFIG.getString("resources.dictionary.Dictionary");
      entireDict = new SimpleDictionary(dictDir + dictFileName, "EntireDictionary", testDictCleanup);
    } else {
      String srcDictFileName = Configurator.CONFIG.getString("resources.dictionary.SrcName");
      String trgDictFileName = Configurator.CONFIG.getString("resources.dictionary.TrgName");      
      entireDict = new SimpleDictionary(new DictHalves(dictDir + srcDictFileName, dictDir + trgDictFileName) , "EntireDictionary");
    }
        
    entireDict.pruneCounts(ridDictNumTrans);
    
    LOG.info("Before comparing with source words, dictionary: "+entireDict.toString());
    LOG.info("Number of src strings: "+entireDict.getAllSrc().size());
    //ANNI update: test dictionary: answers don't need to be in trg context classes
    //Note: in principle shouldn't have to get m_srcEqsToInduct from a dictionary. For now it's an easy hack to ignore target side and use same code
    m_seedDict = new Dictionary(srcEqs, entireDict, "Seed dictionary");
    LOG.info("Initial test dictionary: " + m_seedDict.toString());
    
    m_srcEqsToInduct = selectSrcTokensToInduct(m_seedDict, srcEqs); 

    m_seedDict.retainAllSrc(m_srcEqsToInduct);
    
    LOG.info("Test dictionary: " + m_seedDict.toString());
  }
  
  protected Set<EquivalenceClass> map1To2(Set<EquivalenceClass> all2, Set<EquivalenceClass> some1)
  {
    Set<EquivalenceClass> some2 = new HashSet<EquivalenceClass>();
    
    for (EquivalenceClass two : all2)
    {
      for (EquivalenceClass one : some1)
      {
        if (two.sameEqClass(one))
        { some2.add(two);
        }
      }
    }
    
    return some2;
  }

  /*
  protected void prepareSingleLangDictionaries(boolean src) throws Exception
  {  
    String eqClassName = src ? Configurator.CONFIG.getString("preprocessing.SrcEqClass") : Configurator.CONFIG.getString("preprocessing.TrgEqClass");
    Set<EquivalenceClass> eqClasses = src ? m_contextSrcEqs : m_contextTrgEqs;
    
    LOG.info("Preparing monolingual dictionaries ...");
    
    m_entireDict = new Dictionary(eqClassName, eqClassName, "Monolingual-Dict");
        
    for (EquivalenceClass eq : eqClasses)
    { m_entireDict.add(eq, eq);
    }
    
    m_seedDict = m_testDict = m_entireDict;
    LOG.info("Use, seed and test dictionary: " + m_entireDict.toString());
  }
*/

  protected void assignTypeProp(Set<? extends EquivalenceClass> eqClasses, EqType type)
  {
    Type commonType = new Type(type);
    
    for (EquivalenceClass eq : eqClasses)
    { eq.setProperty(commonType);
    }
  }

  /** Selects tokens for induction. */
  protected Set<EquivalenceClass> selectSrcTokensToInduct(Dictionary dict, Set<EquivalenceClass> srcEqs) throws IOException 
  {
    boolean randomSrc = Configurator.CONFIG.getBoolean("experiments.RandomSource");
    int numToKeep = Configurator.CONFIG.containsKey("experiments.NumSource") ? Configurator.CONFIG.getInt("experiments.NumSource") : -1;
    String outDir = Configurator.CONFIG.getString("output.Path");
    Set<EquivalenceClass> srcSubset = new HashSet<EquivalenceClass>(srcEqs);
    
    srcSubset.retainAll(dict.getAllSrc());

    LinkedList<EquivalenceClass> valList = new LinkedList<EquivalenceClass>(srcSubset);
    
    if ((numToKeep >= 0) && (srcSubset.size() > numToKeep))
    {      
      if (randomSrc)
      {
        srcSubset.clear();

        for (int i = 0; i < numToKeep; i++)
        { srcSubset.add(valList.remove(m_rand.nextInt(valList.size())));
        }
      }
      else
      {
        Collections.sort(valList, new NumberComparator(false));
      
        for (int i = numToKeep; i < valList.size(); i++)
        { srcSubset.remove(valList.get(i));
        }
      }
    }
    
    BufferedWriter writer = new BufferedWriter(new FileWriter(outDir + SRC_TO_INDUCT));
   
    valList.clear();
    valList.addAll(srcSubset);
    Collections.sort(valList, new NumberComparator(false));
    
    for (EquivalenceClass eq : valList)
    { writer.write(((Number)eq.getProperty(Number.class.getName())).getNumber() + "\t" + eq.toString() + "\n");
    }
    
    writer.close();
    
    LOG.info("Selected " + srcSubset.size() + (randomSrc ? " random " : " most frequent ") +  "seed dictionary source classes (see " + outDir + SRC_TO_INDUCT + ").");

    return srcSubset;   
  }
  
  protected String appendSep(String str)
  {
    String ret = (str == null) ? null : str.trim();
    
    if (ret != null && ret.length() > 0 && !ret.endsWith(File.separator))
    { ret += File.separator; 
    }
    
    return ret;
  }

  protected Dictionary m_seedDict;
  protected Dictionary m_projDict = null;
  protected ProbabilisticDictionary m_probProjDict = null;
  
  protected SimpleDictionary m_translitDict = null;  
  protected Set<EquivalenceClass> m_contextSrcEqs;
  protected Set<EquivalenceClass> m_contextTrgEqs;
  protected Set<EquivalenceClass> m_srcEqs;
  protected Set<EquivalenceClass> m_trgEqs;
  protected Set<EquivalenceClass> m_srcEqsToInduct;
  protected double m_numToksInSrc;
  protected double m_numToksInTrg;
  protected double m_maxTokCountInSrc;
  protected double m_maxTokCountInTrg;  
  protected Random m_rand = new Random(1);
  protected String[] m_corpusTitleFilter;
}