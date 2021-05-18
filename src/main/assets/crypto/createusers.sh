#!/bin/bash

#mkdir ha
#cd ha
#
#openssl genrsa -out ha.key.rsa 2048
#openssl pkcs8 -topk8 -inform PEM -outform DER -in ha.key.rsa -out ha.key -nocrypt
#openssl req -new -key ha.key.rsa -out ha.csr -new -sha256
#openssl x509 -req -CA ../ca/ca.pem -CAkey ../ca/ca.key -CAcreateserial -in ha.csr -out ha.pem -days 3650 -sha256
#
#cd ..
#
#mkdir server
#cd server
#
#openssl genrsa -out server.key.rsa 2048
#openssl pkcs8 -topk8 -inform PEM -outform DER -in server.key.rsa -out server.key -nocrypt
#openssl req -new -key server.key.rsa -out server.csr -new -sha256
#openssl x509 -req -CA ../ca/ca.pem -CAkey ../ca/ca.key -CAcreateserial -in server.csr -out server.pem -days 3650 -sha256
#
#cd ..
#
#mkdir byzantineUser1
#cd byzantineUser1
#
#openssl genrsa -out byzantineUser1.key.rsa 2048
#openssl pkcs8 -topk8 -inform PEM -outform DER -in byzantineUser1.key.rsa -out ha.key -nocrypt
#openssl req -new -key byzantineUser1.key.rsa -out byzantineUser1.csr -new -sha256
#openssl x509 -req -CA ../ca/ca.pem -CAkey ../ca/ca.key -CAcreateserial -in byzantineUser1.csr -out byzantineUser1.pem -days 3650 -sha256
#
#cd ..
#
#mkdir byzantineUser2
#cd byzantineUser2
#
#openssl genrsa -out byzantineUser2.key.rsa 2048
#openssl pkcs8 -topk8 -inform PEM -outform DER -in byzantineUser2.key.rsa -out byzantineUser2.key -nocrypt
#openssl req -new -key byzantineUser2.key.rsa -out byzantineUser2.csr -new -sha256
#openssl x509 -req -CA ../ca/ca.pem -CAkey ../ca/ca.key -CAcreateserial -in byzantineUser2.csr -out byzantineUser2.pem -days 3650 -sha256



for VARIABLE in {2..10}
do
username="server$VARIABLE"

mkdir $username
cd $username

openssl genrsa -out $username.key.rsa 2048
openssl pkcs8 -topk8 -inform PEM -outform DER -in $username.key.rsa -out $username.key -nocrypt
openssl req -new -key $username.key.rsa -out $username.csr -new -sha256
openssl x509 -req -CA ../ca/ca.pem -CAkey ../ca/ca.key -CAcreateserial -in $username.csr -out $username.pem -days 3650 -sha256

cd ..

done
