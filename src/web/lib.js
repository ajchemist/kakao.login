export function KakaoAuthorize()
{
  return Kakao.Auth.authorize({
    redirectUri: location.origin + '/kakao/oauth2/callback'
  });
};
