package com.payment.adapter.persistence.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.payment.adapter.persistence")
public class DataSourceConfig {

	public DataSourceConfig() {
		super();
	}
}
