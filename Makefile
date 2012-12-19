default:
	mkdir -p classes
	javac -cp external/Jama-1.0.2.jar:external/servlet-api.jar -d classes `find src -name "*.java"`
	jar cf fig.jar -C classes .
	jar uf fig.jar -C src .
	mkdir -p servlet/WEB-INF/lib
	cp fig.jar servlet/WEB-INF/lib
	(cd servlet && zip -r ../fig.war `/bin/ls | grep -v ^var$$`)

clean:
	rm -rf classes fig.jar fig.war
