create table wte_template (id bigint not null, content longvarbinary, created_at timestamp not null, document_name varchar(255) not null, edited_at timestamp not null, editor_display_name varchar(100), editor_user_id varchar(50) not null, input_class_name varchar(250), language varchar(255) not null, locking_date timestamp, locking_user_display_name varchar(100), locking_user_id varchar(50), version bigint, primary key (id), unique (document_name, language));
create table wte_template_properties (template_id bigint not null, property_value varchar(255), property_key varchar(255) not null, primary key (template_id, property_key));
alter table wte_template_properties add constraint FKC9B09C81E729DB66 foreign key (template_id) references wte_template;
create table wte_gen ( sequence_name varchar(255),  sequence_next_hi_value integer ) ;
