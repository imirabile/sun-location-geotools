# Location Services Resolve POC

* This repo contains code for the proof of concept of the location service using geotools to access local geometry content for both shapefiles and geopackage

Please refer to sun-location-services for the current v3 location services running in both AWS and IKS.

* To `lein run` this project as it is now, you'll need to download both the point mappings file and shapefile from:

https://github.com/TheWeatherCompany/sun-ms-location-service-mappings
https://github.com/TheWeatherCompany/sun-ms-location-service-shapefiles

Place the mapping files inside the folder ./resources/mappings and the shapefile inside the folder ./resources/shapefiles. These files require git large files support. 

* For Github large files:

See https://git-lfs.github.com/
  # sun-location-geotools
