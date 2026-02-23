//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import com.allinweb.ch.customJmsForEmi.abs.ami.intf.*;
import com.allinweb.ch.customJmsForEmi.abs.base.intf.TPrtyObj;
import com.allinweb.ch.customJmsForEmi.abs.base.intf.TPrtyObjVarray256;
import com.allinweb.ch.customJmsForEmi.jms.db.AMIMessageInterface;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import oracle.jdbc.OracleCallableStatement;
import oracle.sql.CLOB;
import oracle.sql.ORAData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMISession implements Session {
    private static final Logger LOGGER;
    protected static final int UNLIMITED_TIMEOUT = -1;
    public static final String ABS_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private int acknowledgeMode;
    private final AMIMessageInterface messageInterface;
    private final AMIConnection connection;
    private Map<String, AMIMessageDispatcher> messageDispatchers;
    private LinkedList<Message> pendingMessages;
    private MessageListener messageListener;

    protected AMISession(
            final AMIConnection connection, final boolean transacted, final int acknowledgeMode, Integer bu_id)
            throws JMSException {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("AMISession transacted=" + transacted + " acknowledgeMode=" + acknowledgeMode);
        }
        this.messageInterface = connection.createMessageInterface(transacted, bu_id);

        if (transacted) {
            this.acknowledgeMode = 0;
        } else {
            if (acknowledgeMode != 1 && acknowledgeMode != 2 && acknowledgeMode != 3 && acknowledgeMode != 0) {
                throw new JMSException("Invalid acknowledge mode");
            }
            if (acknowledgeMode == 2) {
                AMISession.LOGGER.warn("acknowledgeMode Session.CLIENT_ACKNOWLEDGE not supported");
            } else {
                this.acknowledgeMode = 1;
            }
        }
        (this.connection = connection).registerSession(this);
    }

    public BytesMessage createBytesMessage() throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("JMS for avaloq can only exchange text messages");
    }

    public MapMessage createMapMessage() throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("JMS for avaloq can only exchange text messages");
    }

    public Message createMessage() throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("JMS for avaloq can only exchange text messages");
    }

    public ObjectMessage createObjectMessage() throws JMSException {
        return this.createObjectMessage(null);
    }

    public ObjectMessage createObjectMessage(final Serializable object) throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("JMS for avaloq can only exchange text messages");
    }

    public StreamMessage createStreamMessage() throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("JMS for avaloq can only exchange text messages");
    }

    public AMITextMessage createTextMessage() throws JMSException {
        AMISession.LOGGER.trace("createTextMessage()");
        return this.createTextMessage(null);
    }

    public synchronized AMITextMessage createTextMessage(final String text) throws JMSException {
        AMISession.LOGGER.trace("createTextMessage(String)");
        this.checkClosed();
        final AMITextMessage message = new AMITextMessage(text);
        message.setSession(this);
        return message;
    }

    protected AMIMessageInterface getMessageInterface() {
        return this.messageInterface;
    }

    public boolean getTransacted() throws JMSException {
        this.checkClosed();
        try {
            return this.messageInterface.getTransacted();
        } catch (SQLException e) {
            throw this.notifyExceptionListener(e, "getTransacted");
        }
    }

    public int getAcknowledgeMode() throws JMSException {
        this.checkClosed();
        return this.acknowledgeMode;
    }

    public synchronized void acknowledge(final AMIMessage amiJmsMessage, final DeliveryStatus deliveryStatus)
            throws JMSException {
        this.checkClosed();
        if (this.getTransacted() || this.getAcknowledgeMode() != 2) {
            AMISession.LOGGER.warn("Acknowledgement in inconsistent state: transacted=" + this.getTransacted()
                    + " acknowledgeMode=" + this.getAcknowledgeMode());
        }
        final long messageID = amiJmsMessage.getMessageID();
        try {
            final TMsgAckOptObj ackOptions = new TMsgAckOptObj();
            ackOptions.setOmitCustAck(null);
            ackOptions.setIgnCustAckErr("+");
            ackOptions.setErrBehavior(ErrorBehaviour.RETURN.getId());
            final CallableStatement ackStatement = this.messageInterface.get_ACK_OUT_MSG_Statement();
            ackStatement.setLong(2, messageID);
            ackStatement.setLong(3, deliveryStatus.getId());
            ((OracleCallableStatement) ackStatement).setORAData(4, (ORAData) ackOptions);
            ackStatement.setBigDecimal(5, null);
            ackStatement.setString(6, "AMI JMS");
            ackStatement.execute();
            final TMsgResObj resultObject =
                    (TMsgResObj) ((OracleCallableStatement) ackStatement).getORAData(1, TMsgResObj.getORADataFactory());
            if (resultObject == null) {
                throw new JMSException("Failed to acknowledge message. result: <NULL>");
            }
            final CompletionCode completion = CompletionCode.toCompletionCode(resultObject.getCompletion());
            final ReasonCode reason = ReasonCode.toReasonCode(resultObject.getReason());
            final boolean isOk = completion != null && completion.equals(CompletionCode.OK);
            if (!isOk) {
                throw new JMSException(
                        "Failed to ack message to AMI. Reason: " + reason + ". Log ID: " + resultObject.getLogId());
            }
        } catch (SQLException e) {
            throw this.notifyExceptionListener(e, "acknowledge");
        }
    }

    public synchronized void commit() throws JMSException {
        this.checkClosed();
        this.checkTransacted("Can't commit a non transacted session.");
        try {
            this.messageInterface.commit();
        } catch (SQLException e) {
            throw this.notifyExceptionListener(e, "commit");
        }
    }

    public synchronized void rollback() throws JMSException {
        this.checkClosed();
        this.checkTransacted("Can't rollback a non transacted session.");
        try {
            this.messageInterface.rollback();
        } catch (SQLException e) {
            throw this.notifyExceptionListener(e, "rollback");
        }
    }

    public synchronized void close() throws JMSException {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("close");
        }
        this.getConnection().deregisterSession(this);
    }

    public void recover() throws JMSException {
        this.checkClosed();
        this.checkTransacted("Can't recover a non transacted session.");
        throw new AMINotImplementedException(
                "Acknowledge mode Session.CLIENT_ACKNOWLEDGE not supported -> can not recover");
    }

    public synchronized MessageListener getMessageListener() throws JMSException {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("getMessageListener");
        }
        this.checkClosed();
        return this.messageListener;
    }

    public synchronized void setMessageListener(final MessageListener listener) throws JMSException {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("setMessageListener listener=" + listener);
        }
        this.checkClosed();
        this.messageListener = listener;
    }

    public synchronized void run() {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("run");
        }
        try {
            if (this.getMessageListener() != null) {
                while (this.hasPendingMessages()) {
                    try {
                        this.getMessageListener().onMessage(this.popPendingMessage());
                    } catch (RuntimeException e) {
                        AMISession.LOGGER.error("Cannot process pending messages.", (Throwable) e);
                    }
                }
            }
        } catch (JMSException e2) {
            AMISession.LOGGER.error("Cannot process pending messages.", (Throwable) e2);
        }
    }

    public MessageProducer createProducer(final Destination destination) throws JMSException {
        this.checkClosed();
        return (MessageProducer) new AMIMessageProducer(this, destination);
    }

    public MessageConsumer createConsumer(final Destination destination) throws JMSException {
        return this.createConsumer(destination, null);
    }

    public MessageConsumer createConsumer(final Destination destination, final String messageSelector)
            throws JMSException {
        return this.createConsumer(destination, messageSelector, false);
    }

    public MessageConsumer createConsumer(
            final Destination destination, final String messageSelector, final boolean noLocal) throws JMSException {
        this.checkClosed();
        return (MessageConsumer) new AMIMessageConsumer(this, destination, messageSelector);
    }

    public Queue createQueue(final String queueName) throws JMSException {
        this.checkClosed();
        return (Queue) new AMIQueue(queueName);
    }

    public Topic createTopic(final String topicName) throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("publish/subscribe model is not implemented");
    }

    public TopicSubscriber createDurableSubscriber(final Topic topic, final String name) throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("publish/subscribe model is not implemented");
    }

    public TopicSubscriber createDurableSubscriber(
            final Topic topic, final String name, final String messageSelector, final boolean noLocal)
            throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("publish/subscribe model is not implemented");
    }

    public QueueBrowser createBrowser(final Queue queue) throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("queue browser are not implemented");
    }

    public QueueBrowser createBrowser(final Queue queue, final String messageSelector) throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("queue browser are not implemented");
    }

    public TemporaryQueue createTemporaryQueue() throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("temporary queues are not implemented");
    }

    public TemporaryTopic createTemporaryTopic() throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("publish/subscribe model is not implemented");
    }

    public void unsubscribe(final String name) throws JMSException {
        this.checkClosed();
        throw new AMINotImplementedException("publish/subscribe model is not implemented");
    }

    private String getDestinationName(final Destination destination) throws JMSException {
        if (destination == null) {
            return null;
        }
        String destinationName = null;
        if (destination instanceof Queue) {
            destinationName = ((Queue) destination).getQueueName();
        } else if (destination instanceof Topic) {
            throw new AMINotImplementedException("AMI does not support topic destinations");
        }
        return destinationName;
    }

    protected synchronized void send(
            final Message jmsMessage, final boolean disableMessageID, final boolean disableMessageTimestamp)
            throws JMSException {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("send disableMessageID=" + disableMessageID + " disableMessageTimestamp="
                    + disableMessageTimestamp + " message:" + jmsMessage);
        }
        this.checkClosed();
        this.checkDestination(jmsMessage.getJMSDestination());
        AMIDestination.checkDirection(jmsMessage.getJMSDestination(), 1);
        try {
            if (jmsMessage instanceof TextMessage && ((TextMessage) jmsMessage).getText() != null) {
                final String messageText = ((TextMessage) jmsMessage).getText();
                final CallableStatement putStatement = this.messageInterface.get_PUT_MSG_Statement();
                if (messageText.length() == 0) {
                    throw new JMSException("Message text must not be empty");
                }
                final String amiNetwork = AMIDestination.getNetworkName(jmsMessage.getJMSDestination());
                if (amiNetwork.isEmpty()) {
                    throw new JMSException("JMS destination must be specified");
                }
                if (amiNetwork.length() > this.messageInterface.getMsgTypeLen()) {
                    throw new JMSException("network name \"" + amiNetwork + "\" is too long: allowed length = "
                            + this.messageInterface.getMsgTypeLen());
                }
                final String amiMessageType = jmsMessage.getJMSType();
                if (amiMessageType != null && amiMessageType.length() > this.messageInterface.getMsgTypeLen()) {
                    throw new JMSException("JMSType \"" + amiMessageType + "\" is too long: allowed length = "
                            + this.messageInterface.getMsgTypeLen());
                }
                final TMsgPutOptObj putOptionsObject = new TMsgPutOptObj(ErrorBehaviour.RETURN.getId());
                putStatement.setObject(4, putOptionsObject);
                final TMsgObj messageObject = new TMsgObj();
                final int MAPPED_AMI_PRTY_COUNT = 7;
                int p = 0;
                // final Collection<String> propertyNames =
                // (Collection<String>)Collections.list((Enumeration<Object>)jmsMessage.getPropertyNames());

                final Enumeration propertyNames = jmsMessage.getPropertyNames();
                List<String> propertiesList = enumList(propertyNames);
                final TPrtyObj[] propertyArray = new TPrtyObj[7 + propertiesList.size()];
                final SimpleDateFormat dateFormatLong = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                propertyArray[p++] = new TPrtyObj(
                        "ami.jms.Timestamp", dateFormatLong.format(new Timestamp(jmsMessage.getJMSTimestamp())));
                propertyArray[p++] = new TPrtyObj("ami.jms.CorrelationID", jmsMessage.getJMSCorrelationID());
                propertyArray[p++] = new TPrtyObj("ami.jms.MessageID", jmsMessage.getJMSMessageID());
                propertyArray[p++] =
                        new TPrtyObj("ami.jms.ReplyTo", this.getDestinationName(jmsMessage.getJMSReplyTo()));
                final Destination destination = jmsMessage.getJMSDestination();
                if (destination != null) {
                    propertyArray[p++] = new TPrtyObj(
                            (destination instanceof Queue) ? "ami.jms.DestinationQueue" : "ami.jms.DestinationTopic",
                            this.getDestinationName(destination));
                } else {
                    propertyArray[p++] = new TPrtyObj("ami.jms.DestinationQueue", "");
                }
                propertyArray[p++] =
                        new TPrtyObj("ami.jms.DeliveryMode", Integer.toString(jmsMessage.getJMSDeliveryMode()));
                propertyArray[p++] = new TPrtyObj(
                        "ami.jms.Redelivered",
                        (jmsMessage.getJMSRedelivered() ? "+" : Character.valueOf('-')).toString());
                for (final String name : propertiesList) {
                    propertyArray[p++] = new TPrtyObj(name, jmsMessage.getStringProperty(name));
                }
                messageObject.setPrtyList(new TPrtyObjVarray256(propertyArray));
                if (messageText.length() <= this.messageInterface.getMsgLen()) {
                    messageObject.setMsgShort(messageText);
                } else {
                    final CLOB messageTextClob = this.messageInterface.getClob();
                    messageTextClob.trim(0L);
                    final Writer tempClobWriter = messageTextClob.getCharacterOutputStream();
                    try {
                        tempClobWriter.write(messageText);
                        tempClobWriter.flush();
                        tempClobWriter.close();
                    } catch (IOException e) {
                        AMISession.LOGGER.error("Cannot use temporary CLOB for message text.", (Throwable) e);
                    }
                    messageObject.setMsgLong(messageTextClob);
                }
                putStatement.setObject(3, messageObject);
                final TMsgPrtyObj messagePropertyObject = new TMsgPrtyObj();
                messagePropertyObject.setNetw(amiNetwork);
                messagePropertyObject.setMsgType(amiMessageType);
                messagePropertyObject.setExtlMsgNr(
                        (jmsMessage.getJMSCorrelationID() != null)
                                ? jmsMessage.getJMSCorrelationID()
                                : jmsMessage.getJMSMessageID());
                messagePropertyObject.setPrio(9 - jmsMessage.getJMSPriority());
                if (jmsMessage.getJMSExpiration() != 0L) {
                    messagePropertyObject.setExpirDate(new Timestamp(jmsMessage.getJMSExpiration()));
                }
                if (jmsMessage.propertyExists("JMSXDeliveryCount")) {
                    messagePropertyObject.setAttemptCnt(jmsMessage.getIntProperty("JMSXDeliveryCount"));
                }
                putStatement.setObject(2, messagePropertyObject);
                putStatement.execute();
                final TMsgResObj resultObject = (TMsgResObj)
                        ((OracleCallableStatement) putStatement).getORAData(1, TMsgResObj.getORADataFactory());
                final TMsgPrtyObj resultMessageProperrtyObject = (TMsgPrtyObj)
                        ((OracleCallableStatement) putStatement).getORAData(2, TMsgPrtyObj.getORADataFactory());
                if (resultObject == null) {
                    throw new JMSException("Failed to put message. result: <NULL>");
                }
                final CompletionCode completion = CompletionCode.toCompletionCode(resultObject.getCompletion());
                final ReasonCode reason = ReasonCode.toReasonCode(resultObject.getReason());
                final boolean isParseError =
                        reason != null && (reason.equals(ReasonCode.PARSE) || reason.equals(ReasonCode.AFTER_PARSE));
                final boolean isOk = completion != null && completion.equals(CompletionCode.OK);
                /*if (!isOk && !isParseError) {
                    throw new JMSException("Failed to put message to AMI. Reason: " + reason + ". Log ID: " + resultObject.getLogId());
                }
                if (isOk) {
                    ((AMIMessage)jmsMessage).setDeliveryStatus(DeliveryStatus.ACK);
                }
                else if (isParseError) {
                    ((AMIMessage)jmsMessage).setDeliveryStatus(DeliveryStatus.NACK);
                }
                else {
                    ((AMIMessage)jmsMessage).setDeliveryStatus(DeliveryStatus.ERR);
                } */
                if (!disableMessageID) {
                    final BigDecimal messageId = resultMessageProperrtyObject.getMsgId();
                    jmsMessage.setJMSMessageID(
                            (messageId == null) ? null : ("ID:" + this.getDatabaseInstance() + "#" + messageId));
                }
                final Timestamp timestamp = resultMessageProperrtyObject.getTimestamp();
                if (!disableMessageTimestamp && timestamp != null) {
                    jmsMessage.setJMSTimestamp(timestamp.getTime());
                }
            } else {
                if (!(jmsMessage instanceof TextMessage)) {
                    throw new AMINotImplementedException("only JMSTextMessage is supported");
                }
                throw new AMINotImplementedException("JMSTextMessage must have a message set");
            }
        } catch (SQLException e2) {
            AMISession.LOGGER.error("Cannot send message." + jmsMessage);
            throw this.notifyExceptionListener(e2, "send");
        }
    }

    synchronized Message receive(final Destination destination, final long timeout) throws JMSException {
        try {
            return this.receiveInternal(destination, timeout);
        } catch (SQLException e) {
            throw this.notifyExceptionListener(e, "receive");
        } catch (JMSException e2) {
            throw this.notifyExceptionListener((Exception) e2, "receive");
        }
    }

    private synchronized Message receiveInternal(final Destination destination, final long timeout)
            throws JMSException, SQLException {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("receiveInternal destination=" + destination + " timeout=" + timeout);
        }
        this.checkClosed();
        this.checkDestination(destination);
        AMIDestination.checkDirection(destination, 2);
        final long endTimeMillis = (timeout == -1L) ? Long.MAX_VALUE : (System.currentTimeMillis() + timeout);
        long currentTimeMillis = System.currentTimeMillis();
        final long waitIterations = 10L;
        final long maxWaitPeriodMillis = 5000L;
        long waitPeriodMillis = Long.min((endTimeMillis - currentTimeMillis) / 10L, 5000L);
        if (!this.isAcpConnectionStarted()) {
            while (!this.isAcpConnectionStarted() && currentTimeMillis < endTimeMillis) {
                AMISession.LOGGER.trace(".. waiting " + waitPeriodMillis + " milliseconds for session start, maximum "
                        + (endTimeMillis - currentTimeMillis) + " milliseconds");
                try {
                    this.wait(waitPeriodMillis);
                } catch (InterruptedException e) {
                    AMISession.LOGGER.warn("Cannot wait for started ACP connection.", (Throwable) e);
                }
                currentTimeMillis = System.currentTimeMillis();
                waitPeriodMillis = Long.min(endTimeMillis - currentTimeMillis, 5000L);
            }
            AMISession.LOGGER.debug(".. waited " + (timeout - (endTimeMillis - currentTimeMillis))
                    + " milliseconds for session start. isAcpConnectionStarted=" + this.isAcpConnectionStarted());
        }
        currentTimeMillis = System.currentTimeMillis();
        final long acpTimeoutMillis = endTimeMillis - currentTimeMillis;
        if (!this.isAcpConnectionStarted() || acpTimeoutMillis < 0L) {
            AMISession.LOGGER.warn(
                    "Receive timed out before contacting the ACP. network=" + AMIDestination.getNetworkName(destination)
                            + " timeout=" + timeout + " isAcpConnectionStarted=" + this.isAcpConnectionStarted());
            return null;
        }
        final String networkName = AMIDestination.getNetworkName(destination);
        final CallableStatement getStatement = this.messageInterface.get_GET_MSG_Statement();
        final TMsgGetOptObj getOptionsObject = new TMsgGetOptObj();
        getOptionsObject.setErrBehavior(ErrorBehaviour.RETURN.getId());
        getOptionsObject.setNetw(networkName);
        if (timeout == -1L) {
            getOptionsObject.setTimeout(null);
        } else {
            getOptionsObject.setTimeout((int) Math.ceil(acpTimeoutMillis / 1000.0));
        }
        getOptionsObject.setForceClob("+");
        getStatement.setObject(4, getOptionsObject);
        AMISession.LOGGER.trace("getStatement.execute with getOptionsObject=" + getOptionsObject.toString());
        getStatement.execute();
        final TMsgResObj resultObject =
                (TMsgResObj) ((OracleCallableStatement) getStatement).getORAData(1, TMsgResObj.getORADataFactory());
        if (resultObject == null) {
            throw new JMSException("Failed to get message with options " + getOptionsObject.toString());
        }
        final CompletionCode completion = CompletionCode.toCompletionCode(resultObject.getCompletion());
        final ReasonCode reason = ReasonCode.toReasonCode(resultObject.getReason());
        if (completion == null || reason == null) {
            throw new JMSException("Failed to get message with options " + getOptionsObject.toString()
                    + ". Completion: " + completion + " , Reason: " + reason);
        }
        if (!completion.equals(CompletionCode.OK)) {
            if (reason.equals(ReasonCode.TIMEOUT)) {
                AMISession.LOGGER.debug("Timeout getOptionsObject=" + getOptionsObject.toString());
                return null;
            }
            throw new JMSException("Failed to get message with options " + getOptionsObject.toString() + ". Reason: "
                    + reason + ". Log ID: " + resultObject.getLogId());
        } else {
            final TMsgObj messageObject =
                    (TMsgObj) ((OracleCallableStatement) getStatement).getORAData(2, TMsgObj.getORADataFactory());
            final TMsgPrtyObj messagePropertyObject = (TMsgPrtyObj)
                    ((OracleCallableStatement) getStatement).getORAData(3, TMsgPrtyObj.getORADataFactory());
            if (messageObject.getMsgLong() == null && messageObject.getMsgShort() == null) {
                throw new JMSException("Failed to get message with options " + getOptionsObject.toString()
                        + ". TMsgObj contains no message in neither msgLong or msgShort.");
            }
            String messageStr = messageObject.getMsgShort();
            if (messageStr == null) {
                final Clob messageText = (Clob) messageObject.getMsgLong();
                messageStr = messageText.getSubString(1L, (int) messageText.length());
            }
            final AMITextMessage amiTextMessage = this.createTextMessage();
            amiTextMessage.setDeliveryStatus(DeliveryStatus.ACK);
            amiTextMessage.setJMSCorrelationID(messagePropertyObject.getExtlMsgNr());
            amiTextMessage.setJMSMessageID("ID:" + this.getDatabaseInstance() + "#" + messagePropertyObject.getMsgId());
            amiTextMessage.setJMSTimestamp(messagePropertyObject.getTimestamp().getTime());
            amiTextMessage.setJMSType(messagePropertyObject.getMsgType());
            if (messagePropertyObject.getPrio() != null) {
                amiTextMessage.setJMSPriority(9 - messagePropertyObject.getPrio());
            }
            amiTextMessage.setJMSDestination(destination);
            amiTextMessage.setText(messageStr);
            if (messageObject.getPrtyList() != null) {
                final TPrtyObj[] array;
                final TPrtyObj[] propertiesObjectArray =
                        array = messageObject.getPrtyList().getArray();
                for (final TPrtyObj tPrtyObj : array) {
                    if (tPrtyObj != null && tPrtyObj.getName() != null && tPrtyObj.getVal() != null) {
                        amiTextMessage.setStringProperty(tPrtyObj.getName(), tPrtyObj.getVal());
                        if (tPrtyObj.getName().equals("ami.jms.Redelivered")) {
                            if (tPrtyObj.getVal().equals("+")) {
                                amiTextMessage.setJMSRedelivered(true);
                            } else if (tPrtyObj.getVal() == null
                                    || tPrtyObj.getVal().equals("")) {
                                amiTextMessage.setJMSRedelivered(false);
                            } else {
                                AMISession.LOGGER.warn("Unexpected ami.jms.Redelivered value: " + tPrtyObj.getVal()
                                        + ", interpreting as false.");
                                amiTextMessage.setJMSRedelivered(false);
                            }
                        }
                    }
                }
            }
            AMISession.LOGGER.debug("Received message: " + amiTextMessage);
            return (Message) amiTextMessage;
        }
    }

    protected synchronized void start() {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("start");
        }
        this.notify();
    }

    protected synchronized void stop() {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("stop");
        }
    }

    private boolean isAcpConnectionStarted() {
        return this.getConnection().isStarted();
    }

    protected synchronized void registerMessageListener(
            final MessageListener messageListener, final Destination destination) throws JMSException {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug(
                    "registerMessageListener messageListener=" + messageListener + " destination=" + destination);
        }
        AMIDestination.checkDirection(destination, 2);
        final String destinationName = AMIDestination.getDestinationName(destination);
        if (this.messageDispatchers == null) {
            this.messageDispatchers = new Hashtable<String, AMIMessageDispatcher>();
        }
        AMIMessageDispatcher messageDispatcher = this.messageDispatchers.get(destinationName);
        if (messageDispatcher == null) {
            messageDispatcher = new AMIMessageDispatcher(destination);
            messageDispatcher.registerMessageListener(messageListener);
            this.messageDispatchers.put(destinationName, messageDispatcher);
            new Thread(messageDispatcher).start();
        } else {
            messageDispatcher.registerMessageListener(messageListener);
        }
    }

    protected synchronized void removeMessageListener(
            final MessageListener messageListener, final Destination destination) throws JMSException {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug(
                    "removeMessageListener messageListener=" + messageListener + " destination=" + destination);
        }
        if (this.messageDispatchers != null) {
            final String destinationName = AMIDestination.getDestinationName(destination);
            final AMIMessageDispatcher messageDispatcher = this.messageDispatchers.get(destinationName);
            if (messageDispatcher != null) {
                messageDispatcher.removeMessageListener(messageListener);
                if (messageDispatcher.messageListeners.size() == 0) {
                    this.messageDispatchers.remove(destinationName);
                }
            }
        }
    }

    private AMIConnection getConnection() {
        return this.connection;
    }

    private boolean isClosed() throws JMSException {
        try {
            return this.messageInterface.isClosed();
        } catch (SQLException e) {
            throw this.notifyExceptionListener(e, "isClosed");
        }
    }

    protected void checkClosed() throws JMSException {
        if (this.isClosed()) {
            throw new IllegalStateException("Forbidden call on a closed session.");
        }
    }

    private void checkTransacted(final String message) throws JMSException {
        if (!this.getTransacted()) {
            throw new IllegalStateException(message);
        }
    }

    private void checkDestination(final Destination destination) throws JMSException {
        final String dbInstance = this.getDatabaseInstance();
        final String dbInstanceFromDestination = AMIDestination.getDBInstanceName(destination);
        if (dbInstanceFromDestination != null
                && !dbInstanceFromDestination.isEmpty()
                && !AMIDestination.getDBInstanceName(destination).toUpperCase().equals(dbInstance.toUpperCase())) {
            throw new InvalidDestinationException(
                    "Restriction by the AMI-JMS-provider: The DB instance specified in the destination name '"
                            + AMIDestination.getDBInstanceName(destination)
                            + "' must literally be the same as the DB instance '" + dbInstance
                            + "', which is specified in the connection URL '" + this.getURL()
                            + "'\nIMPORTANT: You must use either Oracle Net keyword-value-pair syntax to define port and instance name  'jdbc..@(description=..(connect_data=..(port=PORT)..(service_name=SERVICE)))' or a URL that contains the port and instance name 'jdbc..@[//]HOST:PORT/SERVICE'.");
        }
    }

    protected JMSException notifyExceptionListener(final Exception linkedException, final String info)
            throws JMSException {
        final JMSException jmsException = new JMSException(info + " : " + linkedException.getMessage());
        jmsException.setLinkedException(linkedException);
        return this.getConnection().notifyExceptionListener(jmsException);
    }

    protected JMSException notifyExceptionListener(final SQLException sqlException, final String info)
            throws JMSException {
        final JMSException jmsException = new AMIJMSException(sqlException, info);
        return this.getConnection().notifyExceptionListener(jmsException);
    }

    protected synchronized void pushPendingMessage(final Message message) {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("pushPendingMessage" + message);
        }
        if (this.pendingMessages == null) {
            this.pendingMessages = new LinkedList<Message>();
        }
        this.pendingMessages.add(message);
    }

    private synchronized Message popPendingMessage() {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("popPendingMessage");
        }
        return (this.pendingMessages == null) ? null : this.pendingMessages.removeFirst();
    }

    private synchronized boolean hasPendingMessages() {
        if (AMISession.LOGGER.isDebugEnabled()) {
            AMISession.LOGGER.debug("hasPendingMessages");
        }
        return this.pendingMessages != null && !this.pendingMessages.isEmpty();
    }

    private String getURL() {
        return this.messageInterface.getURL();
    }

    private String getDatabaseInstance() {
        return this.messageInterface.getDatabaseInstance();
    }

    public MessageConsumer createSharedDurableConsumer(final Topic topic, final String name) throws JMSException {
        throw new AMINotImplementedException("AMI does not support shared durable consumers");
    }

    public MessageConsumer createSharedDurableConsumer(
            final Topic topic, final String name, final String messageSelector) throws JMSException {
        throw new AMINotImplementedException("AMI does not support shared durable consumers");
    }

    public MessageConsumer createDurableConsumer(
            final Topic topic, final String name, final String messageSelector, final boolean noLocal)
            throws JMSException {
        throw new AMINotImplementedException("AMI does not support durable consumers");
    }

    public MessageConsumer createDurableConsumer(final Topic topic, final String name) throws JMSException {
        throw new AMINotImplementedException("AMI does not support durable consumers");
    }

    public MessageConsumer createSharedConsumer(final Topic topic, final String sharedSubscriptionName)
            throws JMSException {
        throw new AMINotImplementedException("AMI does not support shared consumers");
    }

    public MessageConsumer createSharedConsumer(
            final Topic topic, final String sharedSubscriptionName, final String messageSelector) throws JMSException {
        throw new AMINotImplementedException("AMI does not support shared consumers");
    }

    static {
        LOGGER = LoggerFactory.getLogger((Class) AMISession.class);
    }

    private class AMIMessageDispatcher implements Runnable {
        Collection<MessageListener> messageListeners;
        Destination destination;

        private AMIMessageDispatcher(final Destination destination) {
            this.messageListeners = new Vector<MessageListener>();
            this.destination = destination;
        }

        private synchronized void registerMessageListener(final MessageListener messageListener) {
            if (AMISession.LOGGER.isDebugEnabled()) {
                AMISession.LOGGER.debug("registerMessageListener messageListener=" + messageListener);
            }
            this.messageListeners.add(messageListener);
        }

        private synchronized void removeMessageListener(final MessageListener messageListener) {
            if (AMISession.LOGGER.isDebugEnabled()) {
                AMISession.LOGGER.debug("removeMessageListener messageListener=" + messageListener);
            }
            this.messageListeners.remove(messageListener);
        }

        @Override
        public void run() {
            if (AMISession.LOGGER.isDebugEnabled()) {
                AMISession.LOGGER.debug("run");
            }
            try {
                while (this.messageListeners.size() > 0 && !AMISession.this.isClosed()) {
                    synchronized (AMISession.this) {
                        synchronized (this) {
                            if (this.messageListeners.size() <= 0 || AMISession.this.isClosed()) {
                                continue;
                            }
                            this.dispatchMessage(AMISession.this.receiveInternal(this.destination, 1000L));
                        }
                    }
                }
            } catch (Exception e) {
                AMISession.LOGGER.error("Cannot dispatch message listener.", (Throwable) e);
                try {
                    AMISession.this.notifyExceptionListener(e, "run");
                } catch (Throwable ignored) {
                    AMISession.LOGGER.error("Ignoring exception in exception listener notification:", ignored);
                }
            }
        }

        private void dispatchMessage(final Message message) {
            if (message != null) {
                for (final MessageListener messageListener : this.messageListeners) {
                    try {
                        messageListener.onMessage(message);
                    } catch (RuntimeException e) {
                        AMISession.LOGGER.error("Cannot dispatch message listener.", (Throwable) e);
                    }
                }
            }
        }
    }

    private static List<String> enumList(Enumeration enumeration) {
        ArrayList<String> elements = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            elements.add(String.valueOf(enumeration.nextElement()));
        }
        return elements;
    }
}
