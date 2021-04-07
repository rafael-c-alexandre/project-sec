#!/bin/bash



for VARIABLE in {4..5}
do
username="user$VARIABLE"

mkdir $username
cd $username

openssl genrsa -out $username.key.rsa 2048
openssl pkcs8 -topk8 -inform PEM -outform DER -in $username.key.rsa -out $username.key -nocrypt
openssl req -new -key $username.key.rsa -out $username.csr
openssl x509 -req -CA ../ca/ca.pem -CAkey ../ca/ca.key -CAcreateserial -in $username.csr -out $username.pem -days 3650
	
cd ..

done
