package com.allinweb.ch;

import com.allinweb.ch.customJmsForEmi.jms.*;

// import com.avaloq.jms.*;

public class Main {
    public static void main(String[] args) {
        int timeout = 30 * 5000;

        String ip = "192.168.1.190";
        String port = "1522";
        String db = "c1v00001";
        String user = "k";
        String psw = "k";
        String dbInstance = port + "/" + db;

        Integer bu = 9;

        String directionIn = ".IN";
        String directionOut = ".OUT";
        // String avqNetwork = "/MDB$AMIT_A"; //"/MDB$AMIT_A_TC";
        // funziona String avqNetwork = "/SWIFT";
        // String msgType = "PINGPONG";
        // funziona String msgType = "SWI.NAK";
        String avqNetwork = "/EUSIC";
        String msgType = "EUSIC.IA10";

        // cambiare messaggio con uno con dati validi
        String msgPrefix = "trp:1+cmt:";
        String correlation = "PayBU9-1";
        String testMsg = "tc=" + correlation
                + ";metatyp=person;wfaini=new;person_type=person_natural;first_name=Cambio_BU;last_name=Ludovici;person_sym=PLULUTA01;wfa=open_store;wfs=;order_nr=;msgin=;msgout=";
        // String testPay = "tc=" + correlation +
        // ";metatyp=pay;wfaini=open_dom;asset=chf;amount=1000;deb_macc=10000341.2001;benef_iban=CH1800778100123456096;benef=Fitness Club Antischlappi 4500 Bergen;wfa=open_prcd;wfs=;order_nr=;msgin=;msgout=";
        // funziona String testPay=
        // "{1:F01PTSBCHMMAXX0000000003}{2:O1031329231227DEUTUS33XXXX00000000002312231929N}{3:{111:001}{121:5911a367-5e60-4a2b-ad80-6cfd3ac105a4}}{4::20:S547594ICP126689:23B:CRED:32A:240212USD100,:33B:USD100,:50F:/CH35888801000337420031/Otto1/Mustermann:57A:PTSBCHMM:58A:/Avaloq Model Bank:59:/CH0888880100013982037MATCHED:71A:OUR:71F:USD100,:72:/INS/GSLDGB2L/INS/CITIUS33XXX:77B:/ORDERRES/GB-}";
        String testPay =
                ">>A10þ<02>098719þ<03>654321þ<15>EURþ<16>20240215þ<17A>100,þ<18>þ<32A>HerrþDr. Ronald StrasslerþSonnenrainþ8024 Schonenbergþ<45I>CH4888880100003412003þ<46A>HerrþBeat GerberþPilatusstrasse 7þ6000 Luzern";
        try {
            AMIQueueConnectionFactory queueConnectionFactory = new AMIQueueConnectionFactory(ip, port, db);
            AMIQueueConnection queueConnection =
                    (AMIQueueConnection) queueConnectionFactory.createQueueConnection(user, psw);
            queueConnection.setClientID(dbInstance);
            AMIQueueSession queueSession = (AMIQueueSession) queueConnection.createQueueSession(false, 0, bu);
            queueConnection.start();

            AMIQueue queueIn = (AMIQueue) queueSession.createQueue(dbInstance + avqNetwork + directionIn);
            AMIQueueSender queueSender = (AMIQueueSender) queueSession.createSender(queueIn);

            AMIQueue queueOut = (AMIQueue) queueSession.createQueue(dbInstance + avqNetwork + directionOut);
            AMIQueueReceiver queueReceiver = (AMIQueueReceiver) queueSession.createReceiver(queueOut);

            AMITextMessage messageIn;
            /* if (args.length == 0){
                messageIn = queueSession.createTextMessage(msgPrefix + testPay);
            } else {
                messageIn = queueSession.createTextMessage(msgPrefix + args[0]);
            } MODIFICHE MARTINO PROVA*/
            messageIn = queueSession.createTextMessage(testPay);
            /* messageIn.setJMSType(msgType);
            messageIn.setJMSCorrelationID(correlation);
            messageIn.setJMSReplyTo(queueOut); */
            print("MSG IN: " + messageIn.getText());

            queueSender.send(messageIn);
            print(messageIn.toString());

            AMITextMessage messageOut;
            boolean return_msg_found = false;
            do {
                messageOut = (AMITextMessage) queueReceiver.receive(timeout);
                if (messageOut != null
                        && messageOut.getJMSCorrelationID() != null
                        && messageOut.getJMSCorrelationID().equals(correlation)) {
                    return_msg_found = true;
                    break;
                }
            } while (messageOut != null);

            if (return_msg_found) {
                print("Trovato msg ritorno >> " + messageOut.getText());
            } else {
                print("AVALOQ NON HA RESTITUITO NULLA");
            }

            queueSender.close();
            queueReceiver.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void print(String s) {
        System.out.println("Engine >> " + s);
    }
}

/*
oracle+cx_oracle://admin:allinweb@192.168.1.183:1521/?service_name=simulava

SWIFT
FIX
SECOM
sic
eusic
RECON

AVALOQ53EDU =
 (DESCRIPTION =
    (ADDRESS_LIST =
      (ADDRESS = (PROTOCOL = TCP)(HOST = 192.168.1.189)(PORT = 1522))
    )
   (CONNECT_DATA =
	(SID = c1v00001)(GLOBAL_NAME = c1v00001.avamdb)))


	4.1
	(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(Host=192.168.1.173)(Port=1522 ))
	(CONNECT_DATA=(SID=s5d00117)(GLOBAL_NAME=s5d00117.avamdb)))
	network = MDB$AMIT_A_TC
 */
