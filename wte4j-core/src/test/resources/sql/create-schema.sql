CREATE TABLE wte4j_gen (SEQUENCE_NAME VARCHAR(255) NOT NULL, SEQUENCE_NEXT BIGINT, PRIMARY KEY (SEQUENCE_NAME));
CREATE TABLE wte4j_template (id BIGINT NOT NULL, content BLOB, created_at TIMESTAMP NOT NULL, document_name VARCHAR(255) NOT NULL, edited_at TIMESTAMP NOT NULL, input_class_name VARCHAR(250), language VARCHAR(255) NOT NULL, locking_date TIMESTAMP, version BIGINT, editor_display_name VARCHAR(100), editor_user_id VARCHAR(50) NOT NULL, locking_user_display_name VARCHAR(100), locking_user_id VARCHAR(50), PRIMARY KEY (id), CONSTRAINT U_WT4JPLT_DOCUMENT_NAME UNIQUE (document_name, language));
CREATE TABLE wte4j_template_content_mapping (template_id BIGINT, conentend_control_id VARCHAR(255) NOT NULL, formatter_definition VARCHAR(250), model_key VARCHAR(250));
CREATE TABLE wte4j_template_properties (template_id BIGINT, property_key VARCHAR(255) NOT NULL, property_value VARCHAR(255));
CREATE INDEX I_WT4JPNG_TEMPLATE_ID ON wte4j_template_content_mapping (template_id);
CREATE INDEX I_WT4JRTS_TEMPLATE_ID ON wte4j_template_properties (template_id);
