/**
 * This file is licensed to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package babel.util.xmlpersistence;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;

/**
 * Simple interface to be implemented by any class so that its objects can be 
 * persisted/unpersisted using StAX.
 */
public interface XMLPersistable
{
  public void persist(XMLStreamWriter writer) throws XMLStreamException;
  public void unpersist(XMLStreamReader reader) throws XMLStreamException;
}
