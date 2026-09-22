-- Run as a PostgreSQL superuser or sufficiently privileged administrator.
-- Replace CHANGE_ME with a strong unique password before execution.

CREATE ROLE survey LOGIN PASSWORD 'CHANGE_ME';
CREATE DATABASE survey OWNER survey;
