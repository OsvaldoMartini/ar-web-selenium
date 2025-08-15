package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.sql.*;
import lombok.Getter;
import lombok.Setter;

/**
 * PerformActions.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
public class PerformDBScripts {
    protected static volatile PerformDBScripts instance;

    @Getter
    @Setter
    public Connection conn = null;

    // Private constructor to prevent instantiation
    private PerformDBScripts() {
        // Initialize if necessary
    }

    public static PerformDBScripts getInstance() {
        if (instance == null) {
            synchronized (PerformDBScripts.class) {
                if (instance == null) {
                    instance = new PerformDBScripts();
                }
            }
        }
        return instance;
    }

    public final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    public final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    public final String CONNECTION_TYPE_SQLITE = "jdbc:sqlite:"; // no parameters needed

    private static final ARPropertyManager arPropertyManager;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
    }

    public void initialize(Connection conn) {
        this.conn = conn;
    }

    public String deleteNullBlocksSQL(String tableName) {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        String foreignKeyColumn = "block".equalsIgnoreCase(tableName) ? "bot_job_id" : "home_banking_id";
        String alias = "";

        // SQLite does not support table alias in DELETE
        if (!"SQLite".equalsIgnoreCase(dataBaseType)) {
            alias = tableName + " ";
        }

        String sql = "DELETE FROM " + tableName + (alias.isEmpty() ? "" : alias)
                + "WHERE " + foreignKeyColumn + " = ? "
                + "AND NOT EXISTS ("
                + "  SELECT 1 FROM instruction bli "
                + "  WHERE bli.block_id = " + (alias.isEmpty() ? tableName + ".id" : alias + ".id")
                + ")";

        return sql;
    }
}
