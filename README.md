# IBackupReorg

Scripts for extracting some data from IPhone backup in ITunes.

## Re-organize files into directory structure

ITunes backup organize files based on hash of filename. As a result, it is impossible to know what the files are. The information about the actual filename is stored in sqlite database `Manifest.db` .

### Generate `mkdir` statements

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

Depending on the OS and filesystem in use, the actual file path may be too long (e.g., Windows directory length limit is 259 characters). The "domain" component is abbreviated. This need to be done consistently throughout the process.

### Generate `mv` statements

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
