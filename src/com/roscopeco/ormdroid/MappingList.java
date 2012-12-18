/*
 * Copyright 2012 Ross Bamford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package com.roscopeco.ormdroid;

import java.util.LinkedList;

/*
 * Maintains a list of mappers, and provides functionality for finding
 * a mapper based on assignability.
 * 
 * New mappings are added at the beginning of the list, to allow users 
 * to override default mappings.
 * 
 * NOT THREAD SAFE!
 */
class MappingList {
  private final LinkedList<TypeMapping> mappings = new LinkedList<TypeMapping>();
  
  void addMapping(TypeMapping mapping) {
    mappings.addFirst(mapping);
  }
  
  void removeMapping(TypeMapping mapping) {
    mappings.remove(mapping);
  }
  
  /*
   * Find mapping, or null if none matches.
   */
  TypeMapping findMapping(Class<?> type) {
    for (TypeMapping mapping : mappings) {
      if (mapping.javaType().isAssignableFrom(type)) {
        return mapping;
      }
    }
    return null;
  }
}
