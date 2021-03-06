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

package com.splicemachine.db.iapi.sql.dictionary;

import com.splicemachine.db.catalog.Dependable;
import com.splicemachine.db.catalog.DependableFinder;
import com.splicemachine.db.catalog.UUID;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.StoredFormatIds;
import com.splicemachine.db.iapi.sql.conn.ConnectionUtil;

import java.sql.SQLException;

/**
 * Created by jfilali on 8/18/16.
 */
public class SchemaPermsDescriptor  extends PermissionsDescriptor {

    private UUID   schemaUUID;
    private String schemaName;
    private String selectPriv;
    private String deletePriv;
    private String insertPriv;
    private String updatePriv;
    private String referencesPriv;
    private String triggerPriv;

    public SchemaPermsDescriptor( DataDictionary dd,
                                 String grantee,
                                 String grantor,
                                 UUID schemaUUID,
                                 String selectPriv,
                                 String deletePriv,
                                 String insertPriv,
                                 String updatePriv,
                                 String referencesPriv,
                                 String triggerPriv) throws StandardException {
        super(dd, grantee, grantor);
        this.schemaUUID = schemaUUID;
        this.selectPriv = selectPriv;
        this.deletePriv = deletePriv;
        this.insertPriv = insertPriv;
        this.updatePriv = updatePriv;
        this.referencesPriv = referencesPriv;
        this.triggerPriv = triggerPriv;
        //schemaUUID can be null only if the constructor with tablePermsUUID
        //has been invoked.
        if (schemaUUID != null)
            try {
                schemaName = dd.getSchemaDescriptor(schemaUUID,
                        ConnectionUtil.getCurrentLCC().getTransactionExecute()).getSchemaName();
            } catch (SQLException e) {
                e.printStackTrace();
            }
    }

        /**
     * This constructor just sets up the key fields of a SchemaPermsDescriptor
     */
    public SchemaPermsDescriptor( DataDictionary dd,
                                 String grantee,
                                 String grantor,
                                 UUID schemaUUID) throws StandardException
    {
        this( dd, grantee, grantor, schemaUUID,
                (String) null, (String) null, (String) null, (String) null, (String) null, (String) null);
    }



    public int getCatalogNumber()
    {
        return DataDictionary.SYSSCHEMAPERMS_CATALOG_NUM;
    }

    /*----- getter functions for rowfactory ------*/
    public UUID getSchemaUUID() { return schemaUUID;}
    public String getSelectPriv() { return selectPriv;}
    public String getDeletePriv() { return deletePriv;}
    public String getInsertPriv() { return insertPriv;}
    public String getUpdatePriv() { return updatePriv;}
    public String getReferencesPriv() { return referencesPriv;}
    public String getTriggerPriv() { return triggerPriv;}

    public String toString()
    {
        return "tablePerms: grantee=" + getGrantee() +
                ",tablePermsUUID=" + getUUID() +
                ",grantor=" + getGrantor() +
                ",schemaUUID=" + getSchemaUUID() +
                ",selectPriv=" + getSelectPriv() +
                ",deletePriv=" + getDeletePriv() +
                ",insertPriv=" + getInsertPriv() +
                ",updatePriv=" + getUpdatePriv() +
                ",referencesPriv=" + getReferencesPriv() +
                ",triggerPriv=" + getTriggerPriv();
    }

    /**
     * @return true iff the key part of this permissions descriptor equals the key part of another permissions
     *         descriptor.
     */
    public boolean equals( Object other)
    {
        if( !( other instanceof SchemaPermsDescriptor))
            return false;
        SchemaPermsDescriptor otherSchemaPerms = (SchemaPermsDescriptor) other;
        return super.keyEquals( otherSchemaPerms) && schemaUUID.equals( otherSchemaPerms.schemaUUID);
    }

    /**
     * @return the hashCode for the key part of this permissions descriptor
     */
    public int hashCode()
    {
        return super.keyHashCode() + schemaUUID.hashCode();
    }

    /**
     * @see PermissionsDescriptor#checkOwner
     */
    public boolean checkOwner(String authorizationId) throws StandardException
    {
        SchemaDescriptor sc = getDataDictionary().getSchemaDescriptor(schemaUUID,null);
        if (sc.getAuthorizationId().equals(authorizationId))
            return true;
        else
            return false;
    }

    //////////////////////////////////////////////
    //
    // PROVIDER INTERFACE
    //
    //////////////////////////////////////////////

    /**
     * Return the name of this Provider.  (Useful for errors.)
     *
     * @return String	The name of this provider.
     */
    public String getObjectName()
    {
        return "Table Privilege on " + schemaName;
    }

    /**
     * Get the provider's type.
     *
     * @return char		The provider's type.
     */
    public String getClassType()
    {
        return Dependable.SCHEMA_PERMISSION;
    }

    /**
     @return the stored form of this provider

     @see Dependable#getDependableFinder
     */
    public DependableFinder getDependableFinder()
    {
        return getDependableFinder(
                StoredFormatIds.SCHEMA_PERMISSION_FINDER_V01_ID);
    }


}
