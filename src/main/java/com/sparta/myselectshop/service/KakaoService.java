package com.sparta.myselectshop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.myselectshop.dto.KakaoUserInfoDto;
import com.sparta.myselectshop.entity.User;
import com.sparta.myselectshop.entity.UserRoleEnum;
import com.sparta.myselectshop.jwt.JwtUtil;
import com.sparta.myselectshop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Slf4j(topic = "KAKAO Login")
@Service
@RequiredArgsConstructor
public class KakaoService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    // 그냥 RestTemplate 이 아니라 우리가 옵션도 추가한 RestTemplate (ResttemplateConfig 에서 Bean 수동으로 등록)
    private final JwtUtil jwtUtil;

    public String kakaoLogin(String code) throws JsonProcessingException {
        // 1. "인가 코드"로 "액세스 토큰" 요청
        String accessToken = getToken(code);

        // 2. 토큰으로 카카오 API 호출 : "액세스 토큰"으로 "카카오 사용자 정보" 가져오기
        KakaoUserInfoDto kakaoUserInfo = getKakaoUserInfo(accessToken);

        // 3. 필요시에 회원가입
        User kakaoUser = registerKakaoUserIfNeeded(kakaoUserInfo);

        // 4. JWT 토큰 반환
        String createToken = jwtUtil.createToken(kakaoUser.getUsername(), kakaoUser.getRole());

        return createToken;
    }

    private String getToken(String code) throws JsonProcessingException {
        // 요청 URL 만들기
        URI uri = UriComponentsBuilder
                .fromUriString("https://kauth.kakao.com")
                .path("/oauth/token")
                .encode()
                .build()
                .toUri();

        // HTTP Header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // HTTP Body 생성
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", "be06cca37b536f44217b65b74c9388a2");
        body.add("redirect_uri", "http://localhost:8080/api/user/kakao/callback");
        body.add("code", code);

        RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
                .post(uri)
                .headers(headers)
                .body(body);

        // HTTP 요청 보내기
        ResponseEntity<String> response = restTemplate.exchange(
                requestEntity,
                String.class
        );

        // HTTP 응답 (JSON) -> 액세스 토큰 파싱
        // 잭슨라이브러리에 ObjectMapper 배웠음, 복습할 것
        JsonNode jsonNode = new ObjectMapper().readTree(response.getBody());
        return jsonNode.get("access_token").asText();
    }






    private KakaoUserInfoDto getKakaoUserInfo(String accessToken) throws JsonProcessingException {
        log.info("액세스토큰 : " + accessToken);
        // 요청 URL 만들기
        URI uri = UriComponentsBuilder
                .fromUriString("https://kapi.kakao.com")
                .path("/v2/user/me")
                .encode()
                .build()
                .toUri();

        // HTTP Header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken); // Bearer : token 이란걸 알려주는 식별자 역할
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
                .post(uri)
                .headers(headers)
                .body(new LinkedMultiValueMap<>());

        // HTTP 요청 보내기
        ResponseEntity<String> response = restTemplate.exchange(
                requestEntity,
                String.class
        );

        JsonNode jsonNode = new ObjectMapper().readTree(response.getBody());
        Long id = jsonNode.get("id").asLong();
        String nickname = jsonNode.get("properties")
                .get("nickname").asText();
        String email = jsonNode.get("kakao_account")
                .get("email").asText(); // .asText 를 통해 String 값을 받아옴
        // KakoTalk 유저 정보에 있는 Json 형태를 보면 이해된다(강의자료에 있음, 딕셔너리 형태로 되어있다)

        log.info("카카오 사용자 정보: " + id + ", " + nickname + ", " + email);
        return new KakaoUserInfoDto(id, nickname, email);
    }

    private User registerKakaoUserIfNeeded(KakaoUserInfoDto kakaoUserInfo) {
        // DB 에 중복된 Kakao Id 가 있는지 확인
        // 한 번이라도 가입이 되어 있으면 회원가입을 시키지 않아야 하므로 확인하는 것
        Long kakaoId = kakaoUserInfo.getId();
        // UserRepository 에 findByKakaoId 메서드 통해 kakoId 가 있는지 확인, 없으면 null 을 반환하고, 있으면 repository에 등록한다
        User kakaoUser = userRepository.findByKakaoId(kakaoId).orElse(null);
        // 있으면 kakaoUser 에 객체가 찾아져서 생성, 없으면 null 반환

        if (kakaoUser == null) { // 회원 가입 된 정보가 없구나! if 문 안에서 회원가입 알고리즘 구성
            // 카카오 사용자 email 동일한 email 가진 회원이 있는지 확인
            // 카카오 이메일을 통해 이메일 회원가입을 하고, 또 카카오 이메일로 가입을 하는 경우 이메일이 중복되기 때문에 있는 알고리즘
            String kakaoEmail = kakaoUserInfo.getEmail();
            User sameEmailUser = userRepository.findByEmail(kakaoEmail).orElse(null);
            if (sameEmailUser != null) { // 회원 가입 폼으로 로그인 했던 사람이랑 같은 회원이구나! 라고 판단
                kakaoUser = sameEmailUser;
                // 기존 회원정보에 카카오 Id 추가
                kakaoUser = kakaoUser.kakaoIdUpdate(kakaoId); // 카카오 아이디로 업데이트(저장) 해줌 , 다음 번엔 if 문 거치지 않고 바로 return kakaoUser
            } else {
                // 신규 회원가입
                // password: random UUID
                String password = UUID.randomUUID().toString(); // 신규 비밀번호를 임의로 UUID 로 만드는 이유 : 로그인 폼을 통한 가입 방지
                String encodedPassword = passwordEncoder.encode(password);

                // email: kakao email
                String email = kakaoUserInfo.getEmail();

                kakaoUser = new User(kakaoUserInfo.getNickname(), encodedPassword, email, UserRoleEnum.USER, kakaoId);
            }

            userRepository.save(kakaoUser);
            // simplejparepository 에서 save 어떻게 구동되는지 확인 해보기
            // .save가 em.persist(영속화) 만 하는게 아니라 상황에 따라서 em.merge 도 한다.
            // @Transational 이 걸려있지않기 때문에 .save 해주는 것. transational 걸지 않는 이유는
            // 상황에 따라 업데이트 일때도, 세이브 일때도 있기 때문이다. 그래서 한 번 수정한 다음에 세이브 하는 걸로 메서드를 호출 해준 것.

        }
        // 첫 if 문 null 이 아닐 시 -> 이미 회원인므로, UserRepository 에서 kakaoUser 찾아서 반환
        // kakoUser 내부에 Username, 유저 권한이 있으니까 이를 통해 Token을 만들어 반환을 하면 JWT 토큰이 브라우저에 set 될거고,
        // 그 토큰을 가지고 추가적인 api 요청을 하면 회원별 상품조회, 회원등록 등이 될 것.
        return kakaoUser;

    }



}