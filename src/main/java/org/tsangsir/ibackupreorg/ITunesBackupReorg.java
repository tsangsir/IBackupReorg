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

/*
.output mkdir.sh

select 'mkdir -p reorg/' || 
replace(replace(replace(replace(replace(
domain,
'AppDomainGroup', 'ADG'),
'AppDomainPlugin', 'ADP'),
'AppDomain', 'AD'),
'SysContainerDomain', 'SCD'),
'SysSharedContainerDomain', 'SSCD')
 || '/' || relativePath from Files
where flags=2
order by relativePath ;

.output mvfiles.sh
select 'mv ' || substr(fileid, 1, 2) || '/' || fileid || ' ' || 'reorg/'
replace(replace(replace(replace(replace(replace(
domain,
'AppDomainGroup', 'ADG'),
'AppDomainPlugin', 'ADP'),
'AppDomain', 'AD'),
'SysContainerDomain', 'SCD'),
'SysSharedContainerDomain', 'SSCD'),
':', '')
 || '/' || relativepath || ' #' || domain
from Files
--where flags in (1, 4)
order by relativePath ;

select max(length(domain || '/' || relativepath )) from files

select distinct 
replace(replace(replace(replace(replace(
domain,
'AppDomainGroup', 'ADG'),
'AppDomainPlugin', 'ADP'),
'AppDomain', 'AD'),
'SysContainerDomain', 'SCD'),
'SysSharedContainerDomain', 'SSCD')
x
 from files
 
  select max(length(
replace(replace(replace(replace(replace(
domain,
'AppDomainGroup', 'ADG'),
'AppDomainPlugin', 'ADP'),
'AppDomain', 'AD'),
'SysContainerDomain', 'SCD'),
'SysSharedContainerDomain', 'SSCD') 
  || '/' || relativepath )) from files

*/


/*
create table domain_replace (
domain varchar(100),
replacement varchar2(20)
);


insert into domain_replace values ('AppDomainGroup-', 'ADG-');
insert into domain_replace values ('AppDomainPlugin-', 'ADP-');
insert into domain_replace values ('AppDomain-', 'AD-');
insert into domain_replace values ('SysContainerDomain-', 'SCD-');
insert into domain_replace values ('SysSharedContainerDomain-', 'SSCD-');

--commit;
-- Check
select * from domain_replace dr1, domain_replace dr2
where dr1.domain like dr2.domain || '%'
and dr1.domain != dr2.domain;


select * from path_replace pr1, path_replace pr2
where pr1.path like pr2.path || '%';


create table path_replace (
path varchar(100),
replacement varchar2(20)
);

insert into path_replace values ('44a724bf37c59a6021560db0f07c083512c4d58d462aaf2ff94c09d398ad7dc2', '44a72...');




select --f.domain, replace(f.domain, dr.domain, dr.replacement), count(*)
ifnull(replace(f.domain, dr.domain, dr.replacement), f.domain), count(*)
from files f 
left join domain_replace dr on f.domain like '%' || dr.domain || '%'
left join path_replace pr on f.relativepath like '%' || pr.path || '%'
where (f.domain like 'AppDomain%'
or f.domain like 'Sys%'
)
group by f.domain, dr.domain, dr.replacement;


create view files_short
as
select f.fileid, 
ifnull(replace(f.domain, dr.domain, dr.replacement), f.domain) domain,
ifnull(replace(f.relativepath, pr.path, pr.replacement), f.relativepath)  relativepath,
f.flags,
f.file
from files f 
left join domain_replace dr on f.domain like '%' || dr.domain || '%'
left join path_replace pr on f.relativepath like '%' || pr.path || '%';


select * from files_short where domain like 'ADP%' LIMIT 10;
select * from files_short where relativepath like '%44a7%' LIMIT 10;

*/
