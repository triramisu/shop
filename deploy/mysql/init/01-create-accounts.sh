#!/usr/bin/env bash
set -Eeuo pipefail

required_variables=(
  MYSQL_DATABASE
  MYSQL_ROOT_PASSWORD
  MYSQL_RUNTIME_USERNAME
  MYSQL_RUNTIME_PASSWORD
  MYSQL_MIGRATION_USERNAME
  MYSQL_MIGRATION_PASSWORD
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Required variable ${variable_name} is missing" >&2
    exit 1
  fi
done

if [[ ! "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "MYSQL_DATABASE contains unsupported characters" >&2
  exit 1
fi

for username in "$MYSQL_RUNTIME_USERNAME" "$MYSQL_MIGRATION_USERNAME"; do
  if [[ ! "$username" =~ ^[A-Za-z0-9_]{1,32}$ ]]; then
    echo "MySQL username contains unsupported characters" >&2
    exit 1
  fi
done

for password in "$MYSQL_RUNTIME_PASSWORD" "$MYSQL_MIGRATION_PASSWORD"; do
  if [[ ! "$password" =~ ^[A-Za-z0-9_+=/@.-]{32,128}$ ]]; then
    echo "MySQL application passwords must be 32-128 safe characters" >&2
    exit 1
  fi
done

mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS '$MYSQL_RUNTIME_USERNAME'@'%'
    IDENTIFIED BY '$MYSQL_RUNTIME_PASSWORD';
ALTER USER '$MYSQL_RUNTIME_USERNAME'@'%'
    IDENTIFIED BY '$MYSQL_RUNTIME_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE
    ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_RUNTIME_USERNAME'@'%';

CREATE USER IF NOT EXISTS '$MYSQL_MIGRATION_USERNAME'@'%'
    IDENTIFIED BY '$MYSQL_MIGRATION_PASSWORD';
ALTER USER '$MYSQL_MIGRATION_USERNAME'@'%'
    IDENTIFIED BY '$MYSQL_MIGRATION_PASSWORD';
GRANT ALL PRIVILEGES
    ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_MIGRATION_USERNAME'@'%';
SQL
