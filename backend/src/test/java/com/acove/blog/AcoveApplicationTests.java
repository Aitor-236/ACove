package com.acove.blog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// test profile 关掉了 Flyway、用的是假数据源，见 src/test/resources/application-test.yml：
// 这样跑单测不依赖本机 MySQL，也不会去改开发库。
@SpringBootTest
@ActiveProfiles("test")
class AcoveApplicationTests {

	@Test
	void contextLoads() {
	}

}
