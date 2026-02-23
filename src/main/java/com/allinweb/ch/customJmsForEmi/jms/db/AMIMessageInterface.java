//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms.db;

import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import oracle.jdbc.OracleCallableStatement;
import oracle.sql.CLOB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMIMessageInterface {
    private static final Logger LOGGER;
    public static final int MAX_MSG_EXTL_ID_LEN = 200;
    private final Connection sqlConnection;
    private final String sqlConnectionUrl;
    private final String sqlConnectionDatabaseInstance;
    private CallableStatement emi_PUT_MSG_statement;
    private CallableStatement emi_GET_MSG_statement;
    private CallableStatement emi_ACK_OUT_MSG_statement;
    private CLOB temporaryClob;
    private final int ami_MSG_TYPE_length;
    private final int ami_MSG_SHORT_length;
    public static final int PUT_MSG__O_RES_OBJ = 1;
    public static final int PUT_MSG__X_MSG_PRTY_OBJ = 2;
    public static final int PUT_MSG__I_MSG_OBJ = 3;
    public static final int PUT_MSG__I_PUT_OPT_OBJ = 4;
    public static final int GET_MSG__O_RES_OBJ = 1;
    public static final int GET_MSG__O_MSG_OBJ = 2;
    public static final int GET_MSG__O_MSG_PRTY_OBJ = 3;
    public static final int GET_MSG__I_GET_OPT_OBJ = 4;
    public static final int ACK_OUT_MSG__O_RES_OBJ = 1;
    public static final int ACK_OUT_MSG__I_MSG_ID = 2;
    public static final int ACK_OUT_MSG__I_MSG_STATUS_ID = 3;
    public static final int ACK_OUT_MSG__I_ACK_OPT_OBJ = 4;
    public static final int ACK_OUT_MSG__I_NETW_ID = 5;
    public static final int ACK_OUT_MSG__I_INFO = 6;

    public AMIMessageInterface(final Connection sqlConnection, final boolean transacted, Integer bu_id)
            throws SQLException {
        if (AMIMessageInterface.LOGGER.isDebugEnabled()) {
            AMIMessageInterface.LOGGER.debug("AMIMessageInterface transacted=" + transacted);
        }

        this.sqlConnection = sqlConnection;
        this.sqlConnectionUrl = sqlConnection.getMetaData().getURL();
        this.sqlConnectionDatabaseInstance = this.calcDatabaseInstance();
        sqlConnection.setAutoCommit(!transacted);
        CallableStatement emi_MSG_TYPE_LEN_statement =
                sqlConnection.prepareCall("{? = call k.msg_intf#.c_msg_type_len}");
        emi_MSG_TYPE_LEN_statement.registerOutParameter(1, 4);
        emi_MSG_TYPE_LEN_statement.execute();
        this.ami_MSG_TYPE_length = emi_MSG_TYPE_LEN_statement.getInt(1);
        CallableStatement emi_MSG_LEN_statement = sqlConnection.prepareCall("{? = call k.msg_intf#.c_msg_len}");
        emi_MSG_LEN_statement.registerOutParameter(1, 4);
        emi_MSG_LEN_statement.execute();
        this.ami_MSG_SHORT_length = emi_MSG_LEN_statement.getInt(1);
        CallableStatement abs_OPEN_SESSION_statement =
                sqlConnection.prepareCall("{call k.session#.open_session(i_is_batch => true)}");
        abs_OPEN_SESSION_statement.execute();

        if (bu_id != null) {
            CallableStatement change_bu_id =
                    sqlConnection.prepareCall("{call session#.session#set_bu_id(i_bu_id => " + bu_id + ")}");
            change_bu_id.execute();
        }
    }

    public CallableStatement get_PUT_MSG_Statement() throws SQLException {
        if (this.emi_PUT_MSG_statement == null) {
            (this.emi_PUT_MSG_statement = this.sqlConnection.prepareCall(
                            "{call k.msg_intf#.netw#put_msg(  o_res_obj      => ?, x_msg_prty_obj => ?, i_msg_obj      => ?, i_put_opt_obj  => ?)}"))
                    .registerOutParameter(1, 2002, "K.T_MSG_RES_OBJ");
            this.emi_PUT_MSG_statement.registerOutParameter(2, 2002, "K.T_MSG_PRTY_OBJ");
        }
        return (CallableStatement) this.getOracleCallableStatement(this.emi_PUT_MSG_statement);
    }

    public CallableStatement get_GET_MSG_Statement() throws SQLException {
        if (this.emi_GET_MSG_statement == null) {
            (this.emi_GET_MSG_statement = this.sqlConnection.prepareCall(
                            "{call k.msg_intf#.netw#get_msg(  o_res_obj      => ?, o_msg_obj      => ?, o_msg_prty_obj => ?, i_get_opt_obj  => ?)}"))
                    .registerOutParameter(1, 2002, "K.T_MSG_RES_OBJ");
            this.emi_GET_MSG_statement.registerOutParameter(2, 2002, "K.T_MSG_OBJ");
            this.emi_GET_MSG_statement.registerOutParameter(3, 2002, "K.T_MSG_PRTY_OBJ");
        }
        return (CallableStatement) this.getOracleCallableStatement(this.emi_GET_MSG_statement);
    }

    public CallableStatement get_ACK_OUT_MSG_Statement() throws SQLException {
        if (this.emi_ACK_OUT_MSG_statement == null) {
            (this.emi_ACK_OUT_MSG_statement = this.sqlConnection.prepareCall(
                            "{call k.msg_intf#.netw#ack_out_msg(  o_res_obj        => ?, i_msg_id         => ?, i_msg_status_id  => ?, i_ack_opt_obj    => ?, i_netw_id        => ?, i_info           => ?)}"))
                    .registerOutParameter(1, 2002, "K.T_MSG_RES_OBJ");
        }
        return (CallableStatement) this.getOracleCallableStatement(this.emi_ACK_OUT_MSG_statement);
    }

    public CLOB getClob() throws SQLException {
        if (this.temporaryClob == null) {
            final CallableStatement createClobStatement =
                    this.sqlConnection.prepareCall("{call dbms_lob.createtemporary(?, true, dbms_lob.session)}");
            createClobStatement.registerOutParameter(1, 2005);
            createClobStatement.execute();
            this.temporaryClob = (CLOB) createClobStatement.getClob(1);
        }
        return this.temporaryClob;
    }

    public int getMsgTypeLen() {
        return this.ami_MSG_TYPE_length;
    }

    public int getMsgLen() {
        return this.ami_MSG_SHORT_length;
    }

    public void commit() throws SQLException {
        this.sqlConnection.commit();
    }

    public void rollback() throws SQLException {
        this.sqlConnection.rollback();
    }

    public boolean getTransacted() throws SQLException {
        return !this.sqlConnection.getAutoCommit();
    }

    public void setTransacted(final boolean transacted) throws SQLException {
        this.sqlConnection.setAutoCommit(!transacted);
    }

    public void close() throws SQLException {
        if (AMIMessageInterface.LOGGER.isDebugEnabled()) {
            AMIMessageInterface.LOGGER.debug("Closing connection." + this);
        }
        if (this.temporaryClob != null) {
            final CallableStatement freeClobStatement =
                    this.sqlConnection.prepareCall("{call dbms_lob.freetemporary(?)}");
            freeClobStatement.setClob(1, (Clob) this.temporaryClob);
            freeClobStatement.execute();
        }
        this.sqlConnection.close();
    }

    public boolean isClosed() throws SQLException {
        return this.sqlConnection.isClosed();
    }

    public String getURL() {
        return this.sqlConnectionUrl;
    }

    public String getDatabaseInstance() {
        return this.sqlConnectionDatabaseInstance;
    }

    public void checkValid() throws SQLException {
        Statement stmt = null;
        final String query = "select 1 from dual";
        boolean processed = false;
        try {
            stmt = this.sqlConnection.createStatement();
            final ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                if (processed) {
                    throw new SQLException("query returned more rows than expected");
                }
                if (rs.getInt(1) != 1) {
                    throw new SQLException("query returned unexpected result");
                }
                processed = true;
            }
        } catch (SQLException e) {
            throw e;
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }

    private String calcDatabaseInstance() throws SQLException {
        String databaseInstanceName = null;
        final String SERVICE_NAME_IDENTIFIER = "SERVICE_NAME";
        final String SID_IDENTIFIER = "SID";
        final String PORT_IDENTIFIER = "PORT";
        final String ASSIGNMENT = "=";
        final String ASSIGNMENT_DELIMITER = ")";
        final String HOST_PORT_SEPARATOR = "@";
        final String HOST_DELIMITER = ":";
        final String connectionUrl = this.sqlConnectionUrl.replaceAll("\\s+", "");
        if (connectionUrl.toUpperCase().contains("SERVICE_NAME")
                || connectionUrl.toUpperCase().contains("SID")) {
            int instanceStart = connectionUrl.toUpperCase().lastIndexOf("PORT") + "PORT".length() + "=".length();
            int instanceEnd = connectionUrl.indexOf(")", instanceStart);
            databaseInstanceName = connectionUrl.substring(instanceStart, instanceEnd);
            String SERVICE_IDENTIFIER = "SERVICE_NAME";
            if (!connectionUrl.toUpperCase().contains("SERVICE_NAME")) {
                SERVICE_IDENTIFIER = "SID";
            }
            instanceStart = connectionUrl.toUpperCase().lastIndexOf(SERVICE_IDENTIFIER)
                    + SERVICE_IDENTIFIER.length()
                    + "=".length();
            instanceEnd = connectionUrl.indexOf(")", instanceStart);
            databaseInstanceName = databaseInstanceName + "/" + connectionUrl.substring(instanceStart, instanceEnd);
        } else {
            if (!connectionUrl.contains("@")) {
                throw new SQLException(
                        "Cannot locate database instance in the connection URL. Use either Oracle Net keyword-value-pair syntax to define PORT and SERVICE 'jdbc..@(description=..(connect_data=..(port=PORT)..(service_name=SERVICE)))' or a URL that contains the PORT and SERVICE 'jdbc..@[//]HOST:PORT/SERVICE'.");
            }
            final int instanceStart = connectionUrl.lastIndexOf(":") + ":".length();
            databaseInstanceName = connectionUrl.substring(instanceStart);
        }
        return databaseInstanceName;
    }

    private OracleCallableStatement getOracleCallableStatement(final CallableStatement statement) throws SQLException {
        if (statement.isWrapperFor(OracleCallableStatement.class)) {
            final OracleCallableStatement oracleCallableStatement = statement.unwrap(OracleCallableStatement.class);
            return oracleCallableStatement;
        }
        throw new IllegalArgumentException("Statement is not an OracleCallableStatement.");
    }

    static {
        LOGGER = LoggerFactory.getLogger((Class) AMIMessageInterface.class);
    }
}
