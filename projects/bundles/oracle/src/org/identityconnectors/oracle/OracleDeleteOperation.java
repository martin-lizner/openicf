/**
 * 
 */
package org.identityconnectors.oracle;

import java.sql.*;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.operations.DeleteOp;

/**
 * @author kitko
 *
 */
class OracleDeleteOperation extends AbstractOracleOperation implements DeleteOp{
    
    
    OracleDeleteOperation(OracleConfiguration cfg, Connection adminConn,Log log) {
        super(cfg, adminConn, log);
    }


    public void delete(ObjectClass objClass, Uid uid, OperationOptions options) {
        OracleConnector.checkObjectClass(objClass, cfg.getConnectorMessages());
        //Currently IDM pass null for options parameter. So there is no way how to decide
        //whether we will do cascade or noCascade delete
        String userName = uid.getUidValue();
        String sql = "drop user \"" + userName + "\"";
        Statement st = null;
        try{
            st = adminConn.createStatement();
            st.executeUpdate(sql);
            adminConn.commit();
        }
        catch(SQLException e){
            SQLUtil.rollbackQuietly(adminConn);
            if("42000".equals(e.getSQLState())){
                throw new UnknownUidException(uid,ObjectClass.ACCOUNT);
            }
        }
        finally{
            SQLUtil.closeQuietly(st);
        }
    }

}