#!/bin/bash

echo "...copying resources..." && sudo service tomcat8 stop && sudo cp target/wildbook-7.0.0-EXPERIMENTAL.war /var/lib/tomcat8/webapps/wildbook.war && sudo chown tomcat8 /var/lib/tomcat8/webapps/wildbook.war && sudo chgrp tomcat8 /var/lib/tomcat8/webapps/wildbook.war && sudo service tomcat8 start & echo "...copyBuild completed successfully"
