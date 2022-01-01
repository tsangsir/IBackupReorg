# IBackupReorg

Scripts for extracting some data from IPhone backup in ITunes.

Two main steps of the process:

1. Re-organizing Files into Directory Hierarchy: Use command line
2. Update File Dates: Use Java program

My Env: iTunes backup on Windows (NTFS directory), manipulated on linux using `ntfs-3g`.

## Re-organizing Files into Directory Hierarchy

ITunes backup rename files according to the hash of the actual filename. The mappings and other metadata are stored in sqlite database `Manifest.db`, a sqlite database.

The useful (usable) information is stored in `Files` table. Important columns:

- `Domain`: iOS classification of the file (??)
- `RelativePath`: folder / directory of the path
- `FileId`: Hash of filename
- `Flags`: 2 means folder. Others unknown

The concept is to move files from `FileId` to `Domain/RelativePath`. The resultant filename may be too long (e.g., Windows directory length limit is 259 characters). It is needed to shorten some `Domain` and `RelativePath`.

## `Domain` and `RelativePath` Replacement
For `Domain`, I perform following replcements. You may perform other replacements:

    create table domain_replace (
    domain varchar(100),
    replacement varchar2(20)
    );
    
    insert into domain_replace values ('AppDomainGroup-', 'ADG-');
    insert into domain_replace values ('AppDomainPlugin-', 'ADP-');
    insert into domain_replace values ('AppDomain-', 'AD-');
    insert into domain_replace values ('SysContainerDomain-', 'SCD-');
    insert into domain_replace values ('SysSharedContainerDomain-', 'SSCD-');


For `RelativePath`, in my case, the main problem is super long profile IDs.  I need to (yes, the following are the actual SQL) :

    create table path_replace (
    path varchar(100),
    replacement varchar2(20)
    );
     
    insert into path_replace values  ('44a724bf37c59a6021560db0f07c083512c4d58d462aaf2ff94c09d398ad7dc2', '44a72...');

Then I create a view to capture the replacements into a view:

    create view files_short
    as
    select f.fileid, 
    ifnull(replace(f.domain, dr.domain, dr.replacement), f.domain) domain,
    ifnull(replace(f.relativepath, pr.path, pr.replacement), f.relativepath) relativepath,
    f.flags,
    f.file
    from files f 
    left join domain_replace dr on f.domain like '%' || dr.domain || '%'
    left join path_replace pr on f.relativepath like '%' || pr.path || '%';

Some checking

    select count(*) from files;
    select count(*) from files_short;
    select * from files_short where domain like 'ADP%' LIMIT 10;
    select * from files_short where relativepath like '%44a7%' LIMIT 10;
    select max(length(domain || '/' || relativepath )) from files_short;



### Generate `mkdir` statements (TODO: use `Files_Short`)

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


### Generate `mv` statements (TODO: use `Files_Short`)

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

## Set file metadata
Metadata like creation and modification timestamp are stored in `Manifest.db`, in plists.

A Java program is used to set metadata.

Pre-requisites:
- dd-plist 1.23 or above
- sqlite-jdbc 3.34.0 or above

See `pom.xml` for detail.

    # java org.tsangsir.ibackupreorg.ITunesBackupReorg <itunesBackupLocation>


Project was developed in Netbeans.

## Possible Improvments

1. Do everything in Java, remove dependency of command line utilities
2. Study `Manifest.db` to extract more information
3. Study iTunes backups to understand the handling of orphan files in the backup, as well as orphan records in `Manifest.db`.
4. Filter out files (not) to be handled. For many people, possibly only `CameraRollDomain` is needed.
5. Expand into a GUI iTunes backup viewer