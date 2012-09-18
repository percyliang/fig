default:
	mkdir -p classes
	javac -cp external/Jama-1.0.2.jar:external/servlet-api.jar -d classes `find src -name "*.java"`
	jar cf fig.jar -C classes .
	jar uf fig.jar -C src .
	cp fig.jar servlet/WEB-INF/lib
