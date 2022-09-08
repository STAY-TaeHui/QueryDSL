package study.querydsl;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.*;

import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
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
        Member a = new Member("member1",1,null);
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

    /*연관관계가 없는 엔티티 외부 조인*/
    @Test
    public void join_on_no_relateion(){
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        queryFactory
            .select(member,team)
            .from(member)
            .leftJoin(team).on(member.username.eq(team.name))
            .fetch();
    }



    /*Fetch Join*/
    @PersistenceUnit
    EntityManagerFactory emf;
    @Test
    public void fetchJoinNo(){
        //영속성 깔끔하게 비우기
        em.flush();
        em.clear();

        Member member = queryFactory
            .selectFrom(QMember.member)
            .where(QMember.member.username.eq("member1"))
            .fetchOne();

        assert member != null;
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

    @Test
    @Commit
    public void test(){
        Member member = new Member("AAA", 10);
        Team team = new Team("AAA");

        member.changTeam(team);

        em.persist(member);
        em.persist(team);

        em.flush();
        em.clear();

        assertThat(member.getTeam()).isEqualTo(team);

    }

    @Test
    public void simpleProjection(){
        List<String> result = queryFactory
            .select(member.username)
            .from(member)
            .fetch();

        for (String s : result)
        {
            System.out.println("s = " + s);
        }

    }

    @Test
    public void tupleProjection(){
        /*
        * Tuple - com.querydsl.core
        * Tuple 을 사용할 땐 Repository 계층정도에서만 사용.
        * Service 계층에서는 사용하지 말 것.
        * */
        List<Tuple> fetch = queryFactory
            .select(member.username, member.age)
            .from(member)
            .fetch();

        for (Tuple tuple : fetch)
        {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println(username);
            System.out.println(age);
        }

    }

    @Test
    public void findDtoByJPQL(){
    List<MemberDto> resultList = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
        .getResultList();

        for (MemberDto memberDto : resultList)
        {
            System.out.println(memberDto);
        }

    }
    @Test
    public void findDtoBySetter(){
        //Setter로 주입
        //기본생성자 필요
        List<MemberDto> fetch = queryFactory
            .select(Projections.bean(MemberDto.class,
                member.username,
                member.age))
            .from(member)
            .fetch();
        for (MemberDto memberDto : fetch)
        {
            System.out.println(memberDto);
        }

    }
    @Test
    public void findDtoByField(){
        //Field로 주입
        List<MemberDto> fetch = queryFactory
            .select(Projections.fields(MemberDto.class,
                member.username,
                member.age))
            .from(member)
            .fetch();
        for (MemberDto memberDto : fetch)
        {
            System.out.println(memberDto);
        }

    }
    @Test
    public void findDtoByConstructor(){
        //해당 dto의 타입이 맞아야 함.
        List<MemberDto> fetch = queryFactory
            .select(Projections.constructor(MemberDto.class,
                member.username,
                member.age))
            .from(member)
            .fetch();
        for (MemberDto memberDto : fetch)
        {
            System.out.println(memberDto);
        }

    }
    @Test
    public void findUserDto(){
        //Dto의 필드이름이 다른경우
        // as 로 Alias를 넣어줄 수 있음.
        List<UserDto> fetch = queryFactory
            .select(Projections.fields(UserDto.class,
                member.username.as("name"),
                member.age))
            .from(member)
            .fetch();
        for (UserDto memberDto : fetch)
        {
            System.out.println(memberDto);
        }

    }
    @Test
    public void findUserDtoByConstructor(){
        //해당 dto의 타입이 맞아야 함.
        List<UserDto> fetch = queryFactory
            .select(Projections.constructor(UserDto.class,
                member.username,
                member.age))
            .from(member)
            .fetch();
        for (UserDto memberDto : fetch)
        {
            System.out.println(memberDto);
        }
    }

    @Test
    public void findDtoByQueryProjection(){
        // 생성자 방식은 런타임오류가 나고
        // 이 방식은 들어가는 타입을 확인할 수 있고, 만약 값이 잘못들어가게 되면 컴파일오류 발생.
        // 단점 : Q파일을 생성해야함.
        // 단점 : DTO가 queryDsl에 대한 의존성이 생김.
        List<MemberDto> fetch = queryFactory
            .select(new QMemberDto(member.username, member.age))
            .from(member)
            .fetch();
        for (MemberDto memberDto : fetch)
        {
            System.out.println(memberDto);
        }

    }
}
