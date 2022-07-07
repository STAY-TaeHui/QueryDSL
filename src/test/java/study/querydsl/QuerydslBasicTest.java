package study.querydsl;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.*;

import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.persistence.TypedQuery;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest
{
    @Autowired
    EntityManager em;
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before(){
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1",10,teamA);
        Member member2 = new Member("member2",20,teamA);
        Member member3 = new Member("member3",30,teamB);
        Member member4 = new Member("member4",40,teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

    }
    @BeforeEach
    public void init(){
        queryFactory = new JPAQueryFactory(em);
    }

    @Test
    public void startJPQL(){
        //member1을 찾아라
        Member findMember = (Member) em.createQuery(
            "select m from Member m where m.username = :username"
                , Member.class)
            .setParameter("username", "member1").getSingleResult();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl(){

        Member findMember = queryFactory
            .select(member)
            .from(member)
            .where(member.username.eq("member1"))
            .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search(){
        Member a = new Member(null,1,null);
        Member findMember = queryFactory.selectFrom(member)
            .where(
                member.username.eq(a.getUsername())
                , member.age.between(10,20)

            )
            .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
}
    /*
    * 1. 회원 나이 내림차순
    * 2. 회원 이름 올림차순
    * 단 2에서 회원 이름이 없으면 마지막에 출력
    * */
    @Test
    public void sort(){
      em.persist(new Member(null,100));
        em.persist(new Member("member5",100));
        em.persist(new Member("member6",100));
        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.eq(100))
            .orderBy(member.age.desc(), member.username.asc().nullsLast())
            .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
    }

    @Test
    public void paging1(){
        List<Member> result = queryFactory
            .selectFrom(member)
            .orderBy(member.username.desc())
            .offset(1)
            .limit(2)
            .fetch();
        assertThat(result.size()).isEqualTo(2);
    }
    @Test
    public void paging2(){
        QueryResults<Member> qu = queryFactory
            .selectFrom(member)
            .orderBy(member.username.desc())
            .offset(1)
            .limit(2)
            .fetchResults();
    }

    @Test
    public void aggregationi(){
        queryFactory
            .select(
                member.count(),
                member.age.sum()
            )
            .from(member)
            .fetch();
    }

    @Test
    public void group() throws Exception{
        List<Tuple> result = queryFactory
            .select(team.name, member.age.avg())
            .from(member)
            .join(member.team, team)
            .groupBy(team.name)
            .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);
    }

    @Test
    public  void join(){
        queryFactory
            .selectFrom(member)
            .join(member.team, team)
            .where(team.name.eq("teamA"))
            .fetch();
}
    @Test
    public void join_on(){
        List<Tuple> result = queryFactory
            .select(member, team)
            .from(member)
            .leftJoin(member.team, team).on(team.name.eq("teamA"))
            .fetch();

        for (Tuple tuple : result)
        {
            System.out.println("tuple :" + tuple);
        }
    }

    /*Fetch Join*/
    @PersistenceUnit
    EntityManagerFactory emf;
    @Test
    public void fetchJoinNo(){
        em.flush();
        em.clear();

        Member member = queryFactory
            .selectFrom(QMember.member)
            .where(QMember.member.username.eq("member1"))
            .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(member.getTeam());
        assertThat(loaded).as("페이조인 미적용").isFalse();

    }/*Fetch Join*/
    @Test
    public void fetchJoinUse(){
        em.flush();
        em.clear();

        Member member = queryFactory
            .selectFrom(QMember.member)
            .join(QMember.member.team,team).fetchJoin()
            .where(QMember.member.username.eq("member1"))
            .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(member.getTeam());
        assertThat(loaded).as("페이조인 적용").isTrue();

    }

    /*            서브쿼리               */
    @Test
    public void subQuery() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.eq(
                select(memberSub.age.max())
                    .from(memberSub)
            ))
            .fetch();
        assertThat(result).extracting("age")
            .containsExactly(40);
    }
    @Test
    public void subQueryGoe() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.goe(//이상
                select(memberSub.age.avg())
                    .from(memberSub)
            ))
            .fetch();
        assertThat(result).extracting("age")
            .containsExactly(30,40);
    }

    @Test
    public void subQueryIn() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.in(
                select(memberSub.age)
                    .from(memberSub)
                    .where(memberSub.age.gt(10))//10살 초과
            ))
            .fetch();
        assertThat(result).extracting("age")
            .containsExactly(20, 30, 40);
    }
    @Test
    public void selectSubQuery(){
        QMember memberSub = new QMember("memberSub");
        List<Tuple> fetch = queryFactory
            .select(member.username,
                select(memberSub.age.avg())
                    .from(memberSub)
            ).from(member)
            .fetch();

        for (Tuple tuple : fetch) {
            System.out.println("username = " + tuple.get(member.username));
            System.out.println("age = " +
                tuple.get(select(memberSub.age.avg())
                    .from(memberSub)));
        }
    }
}
