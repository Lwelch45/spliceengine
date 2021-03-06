/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.iapi.services.io;
/**
  Derby interface for identifying the format id for the
  stored form of an object. Objects of different classes may
  have the same format id if:

  <UL>
  <LI> The objects read and write the same stored forms.
  <LI> The object's getTypeId() method returns the same
  identifier.
  <LI> The objects support all the interfaces the type
  implies.
  </UL>
  */
public interface TypedFormat
{
	/**
	  Get a universally unique identifier for the type of
	  this object. 

	  @return The identifier. (A UUID stuffed in an array
	  of 16 bytes).
	 */	
	int getTypeFormatId();
}
