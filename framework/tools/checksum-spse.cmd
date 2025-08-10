#ketentuan struktur aplikasi sbb:
#[rootdir]
#--framework
#--webapp 
#	  ----conf/application.conf
#untuk generate hash json dari application files
java -jar play-checksum.jar --generate-json /export/home/appserv/spse
#untuk generate integrity file
java -jar play-checksum.jar --generate /home/appserv/spse

