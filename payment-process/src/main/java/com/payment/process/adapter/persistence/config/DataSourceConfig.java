package com.payment.process.adapter.persistence.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = "com.payment.process.adapter.persistence.model")
@EnableJpaRepositories(basePackages = "com.payment.process.adapter.persistence")
public class DataSourceConfig {

	public DataSourceConfig() {
		super();
	}
}
