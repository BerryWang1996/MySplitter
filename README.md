# MySplitter
Light SQL R/W splitter.It's Developing now.

# Settings
### mysplitter.yml
```
mysplitter:
  datasource: druid #dbcp c3p0 (support one)
  readers:
    reader:
      #datasource settings
    reader:
      #datasource settings
  writers:
    ha-mode:
      switch-opportunity: on-error # scheduled on-error-dissolve (support one)
      heartbeat-model: 
        rate: 1s # be-used (support one)
      detection-sql: SELECT 1
      died-alert-handler: 
        com.xxx.xxx # must implements com.mysplitter.advise.DatasourceDiedAlerterAdvise
    writer:
      #datasource settings
    writer:
      #datasource settings
  log:
    enabled: true
    level: info
    show-sql: true
    show-sql-prttey: true
```
