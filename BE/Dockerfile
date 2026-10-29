# Build backend bằng Maven + Java 17
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Chạy WAR trên Tomcat
FROM tomcat:10.1-jdk17-temurin

# Disable Tomcat shutdown port in container
RUN sed -i 's/port="8005" shutdown="SHUTDOWN"/port="-1" shutdown="SHUTDOWN"/' \
    /usr/local/tomcat/conf/server.xml

# Trust all valid AWS RDS Singapore root CAs, without trusting intermediates.
RUN set -eu; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl openssl; \
    rm -rf /var/lib/apt/lists/*; \
    cert_dir="$(mktemp -d)"; \
    trap 'rm -rf "$cert_dir"' EXIT; \
    curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
        https://truststore.pki.rds.amazonaws.com/ap-southeast-1/ap-southeast-1-bundle.pem \
        -o "$cert_dir/bundle.pem"; \
    awk -v dir="$cert_dir" ' \
        /-----BEGIN CERTIFICATE-----/ { n++; file = dir "/rds-" n ".pem" } \
        file != "" { print > file } \
        /-----END CERTIFICATE-----/ { close(file); file = "" } \
        END { if (n == 0 || file != "") exit 1 } \
    ' "$cert_dir/bundle.pem"; \
    # Preserve existing public CAs in JKS, using a temporary generated password.
    # JSSE loads public trust anchors without a password; no runtime secret is needed.
    openssl rand -hex 32 > "$cert_dir/storepass"; \
    keytool -importkeystore -noprompt -srcprotected \
        -srckeystore "$JAVA_HOME/lib/security/cacerts" \
        -destkeystore "$cert_dir/cacerts" -deststoretype JKS \
        -deststorepass:file "$cert_dir/storepass"; \
    imported=0; \
    for cert in "$cert_dir"/rds-*.pem; do \
        subject="$(openssl x509 -in "$cert" -noout -subject -nameopt RFC2253)"; \
        issuer="$(openssl x509 -in "$cert" -noout -issuer -nameopt RFC2253)"; \
        [ "${subject#subject=}" = "${issuer#issuer=}" ] || continue; \
        openssl x509 -in "$cert" -noout -ext basicConstraints | grep -q 'CA:TRUE'; \
        openssl verify -check_ss_sig -no-CApath -no-CAstore -CAfile "$cert" "$cert"; \
        keytool -importcert -noprompt \
            -keystore "$cert_dir/cacerts" -storetype JKS \
            -storepass:file "$cert_dir/storepass" \
            -alias "aws-rds-ap-southeast-1-$(basename "$cert" .pem)" -file "$cert"; \
        imported=$((imported + 1)); \
    done; \
    [ "$imported" -gt 0 ]; \
    cp "$cert_dir/cacerts" "$JAVA_HOME/lib/security/cacerts"

RUN rm -rf /usr/local/tomcat/webapps/ROOT

COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
