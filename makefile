all: org/thbz/CryptEdit/CryptEdit.class org/thbz/CryptEdit/WrongPasswordException.class org/thbz/CryptEdit/PBE.class org/thbz/CryptEdit/TestPBE.class

clean:
	rm org/thbz/CryptEdit/*.class

%.class: %.java
	javac -classpath ".;jar/bcpg-jdk15on-153.jar;jar/bcprov-jdk15on-153.jar"  $<
