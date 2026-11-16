package com.vidkeep;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * vidkeep 后端启动类。
 *
 * <p>通过 SPRING_PROFILES_ACTIVE 区分运行模式：dev / prod / worker。</p>
 */
@SpringBootApplication
@MapperScan("com.vidkeep.module.**.mapper")
public class VideoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoServerApplication.class, args);
    }
}
