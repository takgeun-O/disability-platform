package io.github.takgeun.iyum;

import org.springframework.boot.SpringApplication;

public class TestIyumApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(IyumApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
