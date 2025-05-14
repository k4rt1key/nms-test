package org.nms.constants;

public class Queries
{
    public static class User
    {
        public static final String CREATE_SCHEMA = """
            CREATE TABLE IF NOT EXISTS app_user (
                id SERIAL PRIMARY KEY,
                name VARCHAR(255) UNIQUE NOT NULL,
                password VARCHAR(255) NOT NULL
            );
            """;

        public static final String GET_BY_ID = """
            SELECT * FROM app_user
            WHERE id = $1;
            """;

        public static final String GET_BY_NAME = """
            SELECT * FROM app_user
            WHERE name = $1;
            """;

        public static final String GET_ALL = """
            SELECT * FROM app_user;
            """;

        public static final String INSERT = """
            INSERT INTO app_user (name, password)
            VALUES ($1, $2)
            RETURNING *;
            """;

        public static final String UPDATE = """
            UPDATE app_user
            SET
                name = COALESCE($2, name),
                password = COALESCE($3, password)
            WHERE id = $1
            RETURNING *;
            """;

        public static final String DELETE = """
            DELETE FROM app_user
            WHERE id = $1
            RETURNING *;
            """;
    }

    public static class Credential
    {
        public static final String CREATE_SCHEMA = """
            CREATE TABLE IF NOT EXISTS credential (
                id SERIAL PRIMARY KEY,
                name VARCHAR(255) UNIQUE NOT NULL,
                username VARCHAR(255),
                password VARCHAR(255)
            );
            """;

        public static final String GET_BY_ID = """
            SELECT *
            FROM credential
            WHERE id = $1;
            """;

        public static final String GET_ALL = """
            SELECT *
            FROM credential;
            """;

        public static final String INSERT = """
            INSERT INTO credential (name, username, password)
            VALUES ($1, $2, $3)
            RETURNING *;
            """;

        public static final String UPDATE = """
            UPDATE credential
            SET
                name = COALESCE($2, name),
                username = COALESCE($3, username),
                password = COALESCE($4, password)
            WHERE id = $1
            RETURNING *;
            """;

        public static final String DELETE = """
            DELETE FROM credential
            WHERE id = $1
            RETURNING *;
            """;
    }

    public static class Discovery
    {
        public static final String CREATE_SCHEMA = """
            CREATE TABLE IF NOT EXISTS discovery (
                id SERIAL PRIMARY KEY,
                name VARCHAR(255) UNIQUE NOT NULL,
                ip VARCHAR(255) NOT NULL,
                ip_type VARCHAR(50) NOT NULL,
                status VARCHAR(50) DEFAULT 'PENDING',
                port INTEGER
            );
            """;

        public static final String CREATE_DISCOVERY_CREDENTIAL_SCHEMA = """
            CREATE TABLE IF NOT EXISTS discovery_credential (
                discovery_id INTEGER REFERENCES discovery(id) ON DELETE CASCADE,
                credential_id INTEGER REFERENCES credential(id) ON DELETE CASCADE,
                PRIMARY KEY (discovery_id, credential_id)
            );
            """;

        public static final String CREATE_DISCOVERY_RESULT_SCHEMA = """
            CREATE TABLE IF NOT EXISTS discovery_result (
                discovery_id INTEGER REFERENCES discovery(id) ON DELETE CASCADE,
                credential_id INTEGER REFERENCES credential(id) ON DELETE RESTRICT,
                ip VARCHAR(255) NOT NULL,
                message TEXT,
                status VARCHAR(50) NOT NULL,
                PRIMARY KEY (discovery_id, ip),
                time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
            );
            """;

        public static final String GET_BY_ID = """
           SELECT
                dp.id AS id,
                dp.name AS name,
                dp.ip AS ip,
                dp.ip_type AS ip_type,
                dp.status AS status,
                dp.port AS port,
                ARRAY_AGG(
                    JSON_BUILD_OBJECT(
                        'id', cp.id,
                        'name', cp.name,
                        'username', cp.username,
                        'password', cp.password
                    )
                ) AS credential
            FROM discovery dp
            LEFT JOIN discovery_credential dc ON dp.id = dc.discovery_id
            LEFT JOIN credential cp ON dc.credential_id = cp.id
            WHERE dp.id = $1
            GROUP BY dp.id, dp.name, dp.ip, dp.ip_type, dp.status, dp.port;
           """;

        public static final String GET_WITH_RESULTS_BY_ID = """
            SELECT
                dp.id AS id,
                dp.name AS name,
                dp.ip AS ip,
                dp.ip_type AS ip_type,
                dp.status AS status,
                dp.port AS port,
                COALESCE(
                    json_agg(
                        json_build_object(
                            'ip', dr.ip,
                            'credential', json_build_object(
                                'id', cp.id,
                                'name', cp.name,
                                'username', cp.username,
                                'password', cp.password
                            ),
                            'message', dr.message,
                            'status', dr.status,
                            'time', dr.time
                        )
                    ) FILTER (WHERE dr.ip IS NOT NULL),
                    '[]'
                ) AS results
            FROM discovery dp
            LEFT JOIN discovery_result dr ON dr.discovery_id = dp.id
            LEFT JOIN credential cp ON cp.id = dr.credential_id
            WHERE dp.id = $1
            GROUP BY dp.id, dp.name, dp.ip, dp.ip_type, dp.status, dp.port
            ORDER BY dp.id;
            """;

        public static final String GET_ALL = """
            SELECT
                dp.id AS id,
                dp.name AS name,
                dp.ip AS ip,
                dp.ip_type AS id_type,
                dp.status AS status,
                dp.port AS port,
                ARRAY_AGG(
                    JSON_BUILD_OBJECT(
                        'id', cp.id,
                        'name', cp.name,
                        'username', cp.username,
                        'password', cp.password
                    )
                ) AS credential
            FROM discovery dp
            LEFT JOIN discovery_credential dc ON dp.id = dc.discovery_id
            LEFT JOIN credential cp ON dc.credential_id = cp.id
            GROUP BY dp.id, dp.name, dp.ip, dp.ip_type, dp.status, dp.port;
            """;

        public static final String GET_ALL_WITH_RESULTS = """
            SELECT
                dp.id AS discovery_id,
                dp.name AS discovery_name,
                dp.ip,
                dp.ip_type,
                dp.status AS discovery_status,
                dp.port,
                COALESCE(
                    json_agg(
                        json_build_object(
                            'ip', dr.ip,
                            'credential', json_build_object(
                                'id', cp.id,
                                'name', cp.name,
                                'username', cp.username,
                                'password', cp.password
                            ),
                            'message', dr.message,
                            'status', dr.status,
                            'time', dr.time
                        )
                    ) FILTER (WHERE dr.ip IS NOT NULL),
                    '[]'
                ) AS results
            FROM discovery dp
            LEFT JOIN discovery_result dr ON dr.discovery_id = dp.id
            LEFT JOIN credential cp ON cp.id = dr.credential_id
            GROUP BY dp.id, dp.name, dp.ip, dp.ip_type, dp.status, dp.port
            ORDER BY dp.id;
            """;

        public static final String INSERT = """
            INSERT INTO discovery (
                name,
                ip,
                ip_type,
                status,
                port
            ) VALUES ($1, $2, $3, 'PENDING', $4)
            RETURNING *;
            """;

        public static final String INSERT_RESULT = """
            INSERT INTO discovery_result (
                discovery_id,
                credential_id,
                ip,
                message,
                status
            ) VALUES ($1, $2, $3, $4, $5)
            ON CONFLICT (discovery_id, ip) DO NOTHING
            RETURNING *;
            """;

        public static final String INSERT_CREDENTIAL = """
            INSERT INTO discovery_credential (discovery_id, credential_id)
            VALUES ($1, $2)
            ON CONFLICT (discovery_id, credential_id) DO NOTHING
            RETURNING *;
            """;

        public static final String UPDATE = """
            UPDATE discovery
            SET
                name = COALESCE($2, name),
                ip = COALESCE($3, ip),
                ip_type = COALESCE($4, ip_type),
                port = COALESCE($5, port)
            WHERE id = $1 AND status = 'PENDING'
            RETURNING *;
            """;

        public static final String UPDATE_STATUS = """
            UPDATE discovery
                SET status = COALESCE($2, status)
            WHERE id = $1
            RETURNING *;
            """;

        public static final String DELETE = """
            DELETE FROM discovery
            WHERE id = $1
            RETURNING *;
            """;

        public static final String DELETE_CREDENTIAL = """
            DELETE FROM discovery_credential dc
            USING discovery dp
            WHERE dc.discovery_id = dp.id
              AND dp.status = 'PENDING'
              AND dc.discovery_id = $1
              AND dc.credential_id = $2
            RETURNING *;
            """;

        public static final String DELETE_RESULT = """
            DELETE FROM discovery_result WHERE discovery_id = $1;
            """;
    }

    public static class Monitor
    {
        public static final String CREATE_SCHEMA = """
            CREATE TABLE IF NOT EXISTS monitor (
                id SERIAL PRIMARY KEY,
                ip VARCHAR(255) UNIQUE NOT NULL,
                port INTEGER,
                credential_id INTEGER REFERENCES credential(id) ON DELETE RESTRICT
            );
            """;

        public static final String CREATE_METRIC_GROUP_SCHEMA = """
            CREATE TABLE IF NOT EXISTS metric_group (
                id SERIAL PRIMARY KEY,
                monitor_id INTEGER REFERENCES monitor(id) ON DELETE CASCADE,
                name VARCHAR(50) NOT NULL,
                polling_interval INTEGER NOT NULL,
                isEnabled BOOLEAN NOT NULL DEFAULT true,
                UNIQUE (monitor_id, name)
            );
            """;

        public static final String GET_BY_ID = """
            SELECT p.id, p.ip, p.port,
                   json_build_object(
                       'id', c.id,
                       'username', c.username,
                       'password', c.password
                   ) AS credential,
                   json_agg(json_build_object(
                       'id', m.id,
                       'monitor_id', m.monitor_id,
                       'name', m.name,
                       'polling_interval', m.polling_interval,
                       'isEnabled', m.isEnabled
                   )) AS metric_group
            FROM monitor p
            LEFT JOIN credential c ON p.credential_id = c.id
            LEFT JOIN metric_group m ON p.id = m.monitor_id
            WHERE p.id = $1
            GROUP BY p.id, c.id;
            """;

        public static final String GET_ALL = """
            SELECT p.id, p.ip, p.port,
                   json_build_object(
                       'id', c.id,
                       'username', c.username,
                       'password', c.password
                   ) AS credential,
                   json_agg(json_build_object(
                       'id', m.id,
                       'monitor_id', m.monitor_id,
                       'name', m.name,
                       'polling_interval', m.polling_interval,
                       'isEnabled', m.isEnabled
                   )) AS metric_group
            FROM monitor p
            LEFT JOIN credential c ON p.credential_id = c.id
            LEFT JOIN metric_group m ON p.id = m.monitor_id
            GROUP BY p.id, c.id;
            """;

        public static final String INSERT = """
            WITH discovery_validation AS (
                SELECT dr.ip, dr.credential_id, dp.port
                FROM discovery_result dr
                JOIN discovery dp ON dr.discovery_id = dp.id
                WHERE dr.discovery_id = $1
                AND dr.ip = $2
                AND dr.status = 'COMPLETED'
            ),
            inserted_monitor AS (
                INSERT INTO monitor (ip, port, credential_id)
                SELECT ip, port, credential_id
                FROM discovery_validation
                RETURNING *
            ),
            metric_group_names AS (
                SELECT 'CPUINFO' AS name UNION ALL
                SELECT 'CPUUSAGE' UNION ALL
                SELECT 'UPTIME' UNION ALL
                SELECT 'MEMORY' UNION ALL
                SELECT 'DISK' UNION ALL
                SELECT 'PROCESS' UNION ALL
                SELECT 'NETWORK' UNION ALL
                SELECT 'SYSTEMINFO'
            ),
            inserted_metrics AS (
                INSERT INTO metric_group (monitor_id, name, polling_interval)
                SELECT p.id, m.name, 30
                FROM inserted_monitor p
                CROSS JOIN metric_group_names m
                RETURNING *
            )
            SELECT
                p.*,
                json_build_object(
                    'id', c.id,
                    'name', c.name,
                    'username', c.username,
                    'password', c.password
                ) AS credential,
                COALESCE(
                    (SELECT json_agg(
                        json_build_object(
                            'id', m.id,
                            'monitor_id', m.monitor_id,
                            'name', m.name,
                            'polling_interval', m.polling_interval,
                            'isEnabled', m.isEnabled
                        )
                    )
                    FROM inserted_metrics m
                    WHERE m.monitor_id = p.id),
                    '[]'::json
                ) AS metric_group
            FROM inserted_monitor p
            JOIN credential c ON p.credential_id = c.id;
            """;

        public static final String UPDATE = """
            UPDATE metric_group
            SET polling_interval = COALESCE($2, polling_interval),
                isEnabled = COALESCE($4, isEnabled)
            WHERE monitor_id = $1 AND name = $3 AND EXISTS (
                SELECT 1 FROM monitor p
                WHERE p.id = metric_group.monitor_id
            )
            RETURNING *;
            """;

        public static final String DELETE = """
            DELETE FROM monitor
            WHERE id = $1
            RETURNING *;
            """;
    }

    public static class PollingResult
    {
        public static final String CREATE_SCHEMA = """
            CREATE TABLE IF NOT EXISTS polling_result (
                id SERIAL PRIMARY KEY,
                monitor_id INTEGER REFERENCES monitor(id) ON DELETE CASCADE,
                name VARCHAR(50) NOT NULL,
                data JSONB NOT NULL,
                time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
            );
            """;

        public static final String GET_ALL = """
            SELECT * FROM polling_result;
            """;

        public static final String INSERT = """
            INSERT INTO polling_result (monitor_id, name, data)
            VALUES ($1, $2, $3)
            RETURNING *;
            """;
    }
}