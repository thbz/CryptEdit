SET CRYPTEDIT_HOME="D:\Users\bezecour\Documents\thb\prog\CryptEdit-master"
SET JAVA_HOME=%JAVA_HOME%;%CRYPTEDIT_HOME%\jar\bcpg-jdk15on-153.jar;%CRYPTEDIT_HOME%\jar\bcprov-jdk15on-153.jar

cd org\thbz\CryptEdit

javac -classpath %JAVA_HOME% *.java

cd ..\..\..
