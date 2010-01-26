package babel.content.eqclasses.properties;

import java.util.List;

import babel.content.corpora.accessors.CorpusAccessor;
import babel.content.eqclasses.EquivalenceClass;

/**
 * Collects property values.
 */
public abstract class PropCollector
{ 
  /**
   * @param corpusAccess Corpus from which to collect the property.
   * eqClasses equivalence class for which to gather properties.
   */
  public abstract void collectProperty(CorpusAccessor corpusAccess, List<EquivalenceClass> eqClasses);
}
