package main.lexinduct;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import babel.content.eqclasses.EquivalenceClass;
import babel.content.eqclasses.properties.context.Context;
import babel.content.eqclasses.properties.context.Context.ScoreComparator;
import babel.ranking.EquivClassCandRanking;
import babel.ranking.MRRAggregator;
import babel.ranking.Ranker;
import babel.ranking.Reranker;
import babel.ranking.scorers.Scorer;
import babel.ranking.scorers.context.DictScorer;
import babel.ranking.scorers.context.FungS1Scorer;
import babel.ranking.scorers.edit.EditDistanceScorer;
import babel.ranking.scorers.edit.EditDistanceTranslitScorer;
import babel.ranking.scorers.timedistribution.TimeDistributionCosineScorer;
import babel.util.config.Configurator;
import babel.util.dict.Dictionary;
import babel.util.dict.SimpleDictionary;

public class FreqBinInductor {

  public static final Log LOG = LogFactory.getLog(FreqBinInductor.class);
  protected static int[] K = {1, 5, 10, 20, 30, 40, 50, 60, 80, 100, 200, 300, 400, 500};

  public static void main(String[] args) throws Exception
  {
    LOG.info("\n" + Configurator.getConfigDescriptor());
    
    FreqBinInductor collector = new FreqBinInductor();
    collector.gogo();
  }
  
  protected void gogo() throws Exception
  {
    boolean slidingWindow = Configurator.CONFIG.getBoolean("experiments.time.SlidingWindow");
    int windowSize = Configurator.CONFIG.getInt("experiments.time.WindowSize");
    String outDir = Configurator.CONFIG.getString("output.Path");
    int numThreads = Configurator.CONFIG.getInt("experiments.NumRankingThreads");
    boolean doContext = Configurator.CONFIG.getBoolean("experiments.DoContext");
    boolean doTime = Configurator.CONFIG.getBoolean("experiments.DoTime");
    boolean doEditDist = Configurator.CONFIG.getBoolean("experiments.DoEditDistance");
    boolean doAggregate = Configurator.CONFIG.getBoolean("experiments.DoAggregate");
    int maxNumTrgPerSrc = K[K.length-1];
    
    FreqBinInductPreparer preparer = new FreqBinInductPreparer();
    
    // Prepare equivalence classes
    preparer.prepare();
    preparer.writeSelectedCandidates(outDir + "src.selected");
    
    // Select a subset of src classes to actually induct
    Set<EquivalenceClass> srcSubset = preparer.getSrcEqsToInduct();   
    Set<EquivalenceClass> trgSet = preparer.getTrgEqs();
    
    // Setup scorers
    DictScorer contextScorer = new FungS1Scorer(preparer.getProjDict(), preparer.getMaxSrcTokCount(), preparer.getMaxTrgTokCount());
    Scorer timeScorer = new TimeDistributionCosineScorer(windowSize, slidingWindow);

    SimpleDictionary translitDict = preparer.getTranslitDict();
    Scorer editScorer;
    if (translitDict==null){
    	editScorer = new EditDistanceScorer();
    }
    else{
    	editScorer = new EditDistanceTranslitScorer(translitDict);
    }
    
    
    // Collect and pre-process properties (i.e. project contexts, normalizes distributions)
    preparer.collectContextAndTimeProps(srcSubset, trgSet);
    preparer.prepareContextAndTimeProps(true, srcSubset, contextScorer, timeScorer, false);
    preparer.prepareContextAndTimeProps(false, trgSet, contextScorer, timeScorer, false);
    
    Collection<EquivClassCandRanking> cands;
    Set<Collection<EquivClassCandRanking>> allCands = new HashSet<Collection<EquivClassCandRanking>>();
    int binNum = 0;
    
    for (Set<EquivalenceClass> srcBin : preparer.getBinnedSrcEqs()) {
    
      LOG.info(" --- Ranking candidates from bin " + binNum + " ---");
      allCands.clear();
    
      if (doTime) {
        LOG.info(" - Ranking candidates using time...");  
        cands = rank(timeScorer, srcBin, trgSet, maxNumTrgPerSrc, numThreads);
        evaluate(cands, preparer.getSeedDict(), outDir + "time." + binNum + ".eval");
        EquivClassCandRanking.dumpToFile(preparer.getSeedDict(), cands, outDir + "time." + binNum + ".scored");                
        allCands.add(cands);
      }
    
      if (doContext) {
        LOG.info("Ranking candidates using context...");
        cands = rank(contextScorer, srcBin, trgSet, maxNumTrgPerSrc, 0.0, numThreads);
        evaluate(cands, preparer.getSeedDict(), outDir + "context." + binNum + ".eval");
        EquivClassCandRanking.dumpToFile(preparer.getSeedDict(), cands, outDir + "context." + binNum + ".scored");   
        allCands.add(cands);
      }
    
      if (doEditDist) {
        LOG.info("Ranking candidates using edit distance...");  
        cands = rank(editScorer, srcBin, trgSet, maxNumTrgPerSrc, numThreads);
        evaluate(cands, preparer.getSeedDict(), outDir + "edit." + binNum + ".eval");
        EquivClassCandRanking.dumpToFile(preparer.getSeedDict(), cands, outDir + "edit." + binNum + ".scored");
        allCands.add(cands);
      }
    
      if (doAggregate)
      {
        LOG.info("Aggregating (MRR) all rankings...");  
        MRRAggregator aggregator = new MRRAggregator();
        cands =  aggregator.aggregate(allCands);
        evaluate(cands, preparer.getSeedDict(), outDir + "aggmrr." + binNum + ".eval");
        EquivClassCandRanking.dumpToFile(preparer.getSeedDict(), cands, outDir + "aggmrr." + binNum + ".scored"); 
      }
      
      binNum++;
    }
    
    LOG.info("--- Done ---");
  }
  
  protected Collection<EquivClassCandRanking> rank(Scorer scorer, Set<EquivalenceClass> srcSubset, Set<EquivalenceClass> trgSet, int maxNumberPerSrc, double threshold, int numThreads) throws Exception
  {     
    Ranker ranker = new Ranker(scorer, maxNumberPerSrc, threshold, numThreads);
    return ranker.getBestCandLists(srcSubset, trgSet);
  }

  protected Collection<EquivClassCandRanking> rank(Scorer scorer, Set<EquivalenceClass> srcSubset, Set<EquivalenceClass> trgSet, int maxNumberPerSrc, int numThreads) throws Exception
  {     
    Ranker ranker = new Ranker(scorer, maxNumberPerSrc, numThreads);
    return ranker.getBestCandLists(srcSubset, trgSet);
  }
  
  protected Collection<EquivClassCandRanking> reRank(Scorer scorer, Collection<EquivClassCandRanking> cands)
  {
    Reranker reranker = new Reranker(scorer);    
    return reranker.reRank(cands);
  }

  protected Collection<EquivClassCandRanking> reRank(Scorer scorer, Collection<EquivClassCandRanking> cands, double threshold)
  {
    Reranker reranker = new Reranker(scorer, threshold);    
    return reranker.reRank(cands);
  }
  
  protected void pruneContextsAccordingToScore(Set<EquivalenceClass> srcEqs, Set<EquivalenceClass> trgEqs, DictScorer scorer)
  {
    ScoreComparator comparator = new ScoreComparator(scorer);
    int pruneContextEqs = Configurator.CONFIG.getInt("experiments.context.PruneContextToSize");

    // Prune context
    for (EquivalenceClass ec : srcEqs)
    { ((Context)ec.getProperty(Context.class.getName())).pruneContext(pruneContextEqs, comparator);
    }
    
    for (EquivalenceClass ec : trgEqs)
    { ((Context)ec.getProperty(Context.class.getName())).pruneContext(pruneContextEqs, comparator);
    }
  }
  
  protected void evaluate(Collection<EquivClassCandRanking> cands, Dictionary testDict, String outFileName) throws IOException
  {
    BufferedWriter writer = new BufferedWriter(new FileWriter(outFileName));
    DecimalFormat df = new DecimalFormat("0.00");
    
    writer.write("K\tAccuracy@TopK\tNumInDict");
    writer.newLine();
  
    Set<EquivalenceClass> goldTrans;  
    double oneInTopK, total, accInTopK;
   
    for (int i = 0; i < K.length; i++)
    {
      oneInTopK = 0;
      total = 0;

      for (EquivClassCandRanking ranking : cands)
      {
        goldTrans = testDict.translate(ranking.getEqClass());
        
        if (goldTrans != null)
        {
          oneInTopK += (ranking.numInTopK(goldTrans, K[i]) > 0) ? 1 : 0;
          total++;
        }
      }

      accInTopK = 100.0 * oneInTopK / total;
    
      writer.write(K[i] + "\t" + df.format(accInTopK) + "\t" + total);
      writer.newLine();
    }
    
    writer.close();
  }
  
}
