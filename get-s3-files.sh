#!/bin/bash

wget -O tmp.zip "https://s3.amazonaws.com/psg.qa.twc/shapefiles/shapefiles-$1.tar.gz" 
mkdir $2
tar -zxvf tmp.zip -C $2
rm tmp.zip
