package babel.content.eqclasses;

import java.util.ArrayList;
import java.util.Collection;

import babel.content.eqclasses.properties.Property;

public abstract class EquivalenceClass implements Comparable<EquivalenceClass>
{
  protected static final boolean DEF_CASE_SENSITIVE = false;
  protected static final long NO_ID = -1;
  protected static int CURRENT_EQCLASS_ID;
  
  /**
   * Simple constuctor.
   */
  public EquivalenceClass()
  {
    m_id = NO_ID;
    m_initialized = false;
    m_caseSensitive = DEF_CASE_SENSITIVE;
    m_properties = new ArrayList<Property>();
  }
  
  /**
   * Initializes the equivalence class with the first word.
   * @param id
   * @param word
   * @param caseSensitive
   */
  public void init(String word, boolean caseSensitive)
  {
    if (word == null)
    { throw new IllegalArgumentException();
    }
    
    m_initialized = true;
    m_caseSensitive = caseSensitive;
    m_properties.clear();
  }
  
  public void assignId()
  {    
    if (m_id == NO_ID)
    { m_id = CURRENT_EQCLASS_ID++;
    }
    else
    { throw new IllegalStateException("Class has already been assigned id: " + m_id);
    }
  }

  public long getId()
  { return m_id;
  }
  
  /**
   * Checks if the given word is in the equivalence class. 
   */
  public abstract boolean isInEqClass(String word);
 
  /**
   * If a word is in the eq class, adds it as a variant.
   */
  public abstract boolean addMorph(String word);

  /**
   * @return All words which were added.
   */
  public abstract Collection<String> getAllWords();
  
  public abstract String getStem();
  
  public abstract String persistToString();
  
  public abstract void unpersistFromString(String str) throws Exception;  
  
  /**
   * Checks if the other object represents the same equivalence class.
   */
  public boolean sameEqClass(EquivalenceClass eq)
  { return equals(eq);
  }

  public abstract boolean merge(EquivalenceClass eq);
  
  /**
   * @return true iff EquivalenceClass is case sensitive
   */
  public boolean isCaseSensitive()
  {
    return m_caseSensitive;
  }
  
  public int hashCode()
  {
    return getStem().hashCode();
  }
  
  public boolean equals(Object obj)
  {
    return ((obj != null) && (obj instanceof EquivalenceClass) && (compareTo((EquivalenceClass)obj) == 0));
  }

  public int compareTo(EquivalenceClass eq)
  {
    if (eq == null)
    { throw new NullPointerException();
    }
    else if (!getClass().equals(eq.getClass()) || !m_initialized || !eq.m_initialized || (m_caseSensitive != eq.m_caseSensitive))
    { throw new RuntimeException(); 
    }

    return getStem().compareTo(eq.getStem());
  }
  
  /**
   * Return a property for a given propery ID, or null if none found.
   */
  public Property getProperty(String propId)
  {
    Property foundProp = null;
    
    // A simple linear search will do [there won't be many properties]
    if (propId != null)
    {
      for (Property curProp: m_properties)
      {
        if (propId.equals(curProp.getPropertyId()))
        {
          foundProp = curProp;
          break;
        }
      }
    }
    
    return foundProp;
  }
  
  /**
   * Adds a  property to the propery list. If propery with the same ID already
   * present - it is removed first.
   * @param prop
   */
  public void setProperty(Property prop)
  {
    if (prop != null)
    {
      String propId = prop.getPropertyId();
      boolean removed = false;
      int numProps = m_properties.size();
      
      // First, remove an old property with the same id, if it is already there
      for (int curPropIdx = 0; (!removed) && (curPropIdx < numProps); curPropIdx++)
      {
        if (propId.equals(m_properties.get(curPropIdx).getPropertyId()))
        {
          m_properties.remove(curPropIdx);
          removed = true;
        }
      }
      
      // Add new property
      m_properties.add(prop);
    }
  }
  
  /**
   * Removes a property with the given property Id.
   * @param propId
   * @return true iff property was found and removed
   */
  public boolean removeProperty(String propId)
  {
    boolean found = true;
    
    for (int propIdx = 0; (propId != null) && (!found) && (propIdx < m_properties.size()); propIdx++)
    {
      if (found = propId.equals(m_properties.get(propIdx).getPropertyId()))
      {
        m_properties.remove(propIdx);
      }
    }
    
    return found;
  }
  
  /**
   * Should be called from all public methods (except for init) to make sure
   * the object was initialzied.
   */
  protected void checkInitialized()
  {
    if (!m_initialized)
    { throw new IllegalStateException("Must be initialized first.");
    }
  }
  
  /**
   * Converts a sting to the normal form.
   * @param word given string
   * @return converted string
   */
  public static String getWordOfAppropriateForm(String word, boolean caseSensitive)
  {
    return caseSensitive ? word : 
      ((word == null) ? null : word.toLowerCase().trim());  
  }
  
  public static void test() {}
  
  
  /** Unique id of the EquivalenceClass. */ 
  protected long m_id;
  /** True iff init was called. */
  protected boolean m_initialized;
  /** True iff EquivalenceClass is case sensitive. */
  protected boolean m_caseSensitive;
  /** List of properties. */
  protected ArrayList<Property> m_properties;
}