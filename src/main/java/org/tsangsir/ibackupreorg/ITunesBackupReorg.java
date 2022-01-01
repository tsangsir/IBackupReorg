/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.tsangsir.ibackupreorg;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tathong
 */
public class ITunesBackupReorg {
    
    String itunesBackupLocation;

    /*
    Map<String, String> domainMap = Map.of(
        "AppDomainGroup", "ADG",
        "AppDomainPlugin", "ADP",
        "AppDomain", "AD",
        "SysContainerDomain", "SCD",
        "SysSharedContainerDomain", "SSCD"
    );
    */
    Connection conn = null;
    
    public ITunesBackupReorg(String itunesBackupLocation) {
        this.itunesBackupLocation = itunesBackupLocation;
    }

    public void connect() {
        try {
            // db parameters
            String url = "jdbc:sqlite:" + itunesBackupLocation + "/Manifest.db";
            // create a connection to the database
            conn = DriverManager.getConnection(url);
            
            System.out.println("Connection to SQLite has been established.");
            
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } 
    }
    
    private void disconnect() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
        
    }
    
    public void process()  {
        String fileId, domain, relativePath;
        int flags;
        InputStream filePlist;
        String sql = "SELECT fileID, domain, relativePath, flags, file from files_short where domain='CameraRollDomain' and relativePath like '%DCIM%HEIC' and relativePath not like '%Thumbnails%' limit 10";
        
        Statement stmt;
        try {
            stmt = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);
            // loop through the result set
            while (rs.next()) {
                fileId = rs.getString("fileID");
                domain = rs.getString("domain");
                relativePath = rs.getString("relativePath");
                filePlist = rs.getBinaryStream("file");
                flags = rs.getInt("flags");

                processOne(domain, relativePath, filePlist);

            }
            rs.close();
            stmt.close();
        } catch (SQLException ex) {
            Logger.getLogger(ITunesBackupReorg.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void processOne(String domain, String relativePath, InputStream filePlist) {
        int pos;
        String reorgPath;
        /*
        String domPrefix;
        String domSuffix;
        pos = domain.indexOf("-");
        if (pos > 0) {
            domPrefix = domain.substring(0, pos);
            domSuffix = domain.substring(pos);
            domain = domainMap.getOrDefault(domPrefix, domPrefix) + domSuffix;
        }
        */
        reorgPath = itunesBackupLocation + "/../reorg/" + domain + "/" + relativePath;

        try {
            NSDictionary rootDict = (NSDictionary)PropertyListParser.parse(filePlist);
            NSObject objDict = ((NSArray)rootDict.objectForKey("$objects")).getArray()[1];
            FileTime lastModified = FileTime.fromMillis(((NSNumber)((NSDictionary)objDict).objectForKey("LastModified")).longValue()*1000);
            FileTime birth = FileTime.fromMillis(((NSNumber)((NSDictionary)objDict).objectForKey("Birth")).longValue()*1000);
            File f = new File(reorgPath);
            System.out.println(f.getAbsolutePath());
            System.out.println("     LastModified: " + lastModified.toString() + ", Birth: " + birth.toString());
            if (f.exists()) {
                BasicFileAttributeView           attributes =
                        Files.getFileAttributeView(f.toPath(), BasicFileAttributeView.class);
                attributes.setTimes(lastModified, null /* last accessed */, birth);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }
        
    public static void main(String[] args) throws SQLException{

        ITunesBackupReorg itbr = new ITunesBackupReorg(args[1]);
        itbr.connect();
        itbr.process();
        itbr.disconnect();

    }
}
