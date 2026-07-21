package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommandOperationCodecConnectionTest {
    @Mock
    private PerformDataBase database;

    @Mock
    private Connection parentConnection;

    @Mock
    private Connection variableConnection;

    @Mock
    private PreparedStatement parentStatement;

    @Mock
    private PreparedStatement variableStatement;

    @Mock
    private ResultSet parentResult;

    @Mock
    private ResultSet variableResult;

    @InjectMocks
    private CommandOperationCodec codec;

    @Test
    void closesRelationshipConnectionsAfterEncoding() throws Exception {
        when(database.getConnection()).thenReturn(parentConnection, variableConnection);
        when(parentConnection.prepareStatement(anyString())).thenReturn(parentStatement);
        when(variableConnection.prepareStatement(anyString())).thenReturn(variableStatement);
        when(parentStatement.executeQuery()).thenReturn(parentResult);
        when(variableStatement.executeQuery()).thenReturn(variableResult);
        when(parentResult.next()).thenReturn(true);
        when(parentResult.getString("name")).thenReturn("Amount");
        when(variableResult.next()).thenReturn(true);
        when(variableResult.getString("type")).thenReturn("#Numeric");
        when(variableResult.getString("name")).thenReturn("amount");
        when(variableResult.getString("value")).thenReturn("10");
        JsonObject body = new JsonObject();
        body.addProperty("parentId", 11);
        body.addProperty("variableId", 22);

        assertEquals("Amount:10", codec.encode(body, "SET"));

        verify(parentConnection).close();
        verify(variableConnection).close();
    }
}
