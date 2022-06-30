package study.querydsl;

import javax.persistence.EntityManager;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import study.querydsl.entity.Hello;
import study.querydsl.entity.QHello;

@SpringBootTest
class QuerydslApplicationTests
{
    @Autowired
    EntityManager em;

    @Test
    void contextLoads()
    {
        Hello hello = new Hello();
        em.persist(hello);

        JPAQueryFactory query = new JPAQueryFactory(em);
        QHello qHello = new QHello("h");

        Hello result = query
            .selectFrom(qHello)
            .fetchOne();

        Assertions.assertThat(result).isEqualTo(hello);
    }

}
