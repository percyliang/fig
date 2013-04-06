NAME := fig
DEPS := $(shell ls external/*.jar) $(shell find src -name "*.java")

default: $(NAME).war

classes: $(DEPS)
	mkdir -p classes
	javac -d classes -cp `echo external/*.jar | sed -e 's/ /:/g'` `find src -name "*.java"`
	touch classes

$(NAME).jar: classes
	jar cf $(NAME).jar -C classes .
	jar uf $(NAME).jar -C src .

servlet: $(NAME).jar
	mkdir -p servlet/WEB-INF/lib
	cp fig.jar servlet/WEB-INF/lib
	touch servlet

$(NAME).war: servlet
	(cd servlet && zip -qr ../fig.war `/bin/ls | grep -v ^var$$`)

clean:
	rm -rf classes fig.jar fig.war
