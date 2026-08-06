package com.allinweb.ch.facade;

import com.allinweb.ch.util.ExtractedData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/** Durable, Bot Job-scoped storage for client-edited synthetic execution data. */
public final class ExcelSyntheticDatasetStore {
    private static final Object SCHEMA_LOCK = new Object();
    private static final Gson GSON = new Gson();

    public ExtractedData load(int homeBankingId, int botJobId) throws SQLException {
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            ensureTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT dataset_json FROM bot_job_synthetic_excel_data"
                            + " WHERE organization_id=? AND home_banking_id=? AND bot_job_id=?")) {
                statement.setInt(1, homeBankingId);
                statement.setInt(2, homeBankingId);
                statement.setInt(3, botJobId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? decode(rows.getString(1)) : null;
                }
            }
        }
    }

    public void save(int homeBankingId, int botJobId, ExtractedData data) throws SQLException {
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            ensureTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bot_job_synthetic_excel_data"
                            + " (organization_id,home_banking_id,bot_job_id,dataset_json,created_at,updated_at)"
                            + " VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"
                            + " ON CONFLICT(organization_id,home_banking_id,bot_job_id)"
                            + " DO UPDATE SET dataset_json=excluded.dataset_json,updated_at=CURRENT_TIMESTAMP")) {
                statement.setInt(1, homeBankingId);
                statement.setInt(2, homeBankingId);
                statement.setInt(3, botJobId);
                statement.setString(4, encode(data));
                statement.executeUpdate();
            }
        }
    }

    private static void ensureTable(Connection connection) throws SQLException {
        synchronized (SCHEMA_LOCK) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS bot_job_synthetic_excel_data ("
                                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                + "organization_id INTEGER NOT NULL,"
                                + "home_banking_id INTEGER NOT NULL,"
                                + "bot_job_id INTEGER NOT NULL,"
                                + "dataset_json TEXT NOT NULL,"
                                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                                + "UNIQUE(organization_id,home_banking_id,bot_job_id),"
                                + "FOREIGN KEY(organization_id) REFERENCES home_banking(id) ON DELETE CASCADE,"
                                + "FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE,"
                                + "FOREIGN KEY(bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE)"
                );
            }
        }
    }

    private static String encode(ExtractedData data) {
        JsonArray blocks = new JsonArray();
        for (String blockName : data.getBlocks()) {
            JsonObject block = new JsonObject();
            block.addProperty("name", blockName);
            JsonArray rows = new JsonArray();
            for (int row = 0; row < data.getNumberOfDataRows(); row++) {
                JsonObject values = new JsonObject();
                for (Map.Entry<String, String> value : data.getRowFieldValues(blockName, row).entrySet()) {
                    if (value.getValue() == null) values.add(value.getKey(), null);
                    else values.addProperty(value.getKey(), value.getValue());
                }
                rows.add(values);
            }
            block.add("rows", rows);
            blocks.add(block);
        }
        return GSON.toJson(blocks);
    }

    private static ExtractedData decode(String json) {
        ExtractedData data = new ExtractedData();
        if (json == null || json.isBlank()) return data;
        JsonArray blocks = GSON.fromJson(json, JsonArray.class);
        for (JsonElement blockElement : blocks) {
            JsonObject block = blockElement.getAsJsonObject();
            String blockName = block.get("name").getAsString();
            JsonArray rows = block.getAsJsonArray("rows");
            for (int row = 0; row < rows.size(); row++) {
                for (Map.Entry<String, JsonElement> value : rows.get(row).getAsJsonObject().entrySet()) {
                    data.addFieldValue(blockName, value.getKey(),
                            value.getValue().isJsonNull() ? null : value.getValue().getAsString(), row);
                }
            }
        }
        return data;
    }
}
