//package com.allinweb.ch.tests;
//
//import com.healthmarketscience.jackcess.Database;
//import com.healthmarketscience.jackcess.Database.FileFormat;
//import com.healthmarketscience.jackcess.DatabaseBuilder;
//import java.io.File;
//import java.io.IOException;
//
//public class AccessVersionChecker {
//
//    public static void main(String[] args) {
//        String dbPath = "path_to_your_db.accdb";
//        try {
//            // Use DatabaseBuilder to open the database
//            Database db = DatabaseBuilder.open(new File(dbPath));
//            FileFormat format = db.getFileFormat();
//
//            switch (format) {
//                case V1997:
//                    System.out.println("Access 97 format");
//                    break;
//                case V2000:
//                    System.out.println("Access 2000 format");
//                    break;
//                case V2003:
//                    System.out.println("Access 2003 format");
//                    break;
//                case V2007:
//                    System.out.println("Access 2007 format");
//                    break;
//                case V2010:
//                    System.out.println("Access 2010 format");
//                    break;
//                case V2016:
//                    System.out.println("Access 2016 format");
//                    break;
//                default:
//                    System.out.println("Unknown format");
//            }
//        } catch (IOException e) {
//            System.out.println(e.getMessage());
//        }
//    }
//}
