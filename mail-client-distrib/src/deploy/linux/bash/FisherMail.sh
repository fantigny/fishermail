#!/bin/bash
echo FisherMail is starting...
execPath="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
$execPath/jre/bin/java \
	-client \
	-jar \
	$execPath/FisherMail.jar \
		0</dev/null \
		1>/dev/null \
		2>/dev/null \
		&
