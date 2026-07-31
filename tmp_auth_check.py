import json, urllib.request, urllib.error

login_data = json.dumps({'username':'operator','password':'password'}).encode()
req = urllib.request.Request('http://localhost:8080/api/auth/login', data=login_data, headers={'Content-Type':'application/json'})
with urllib.request.urlopen(req) as res:
    token = json.load(res)['token']
print('TOKEN', token)

req2 = urllib.request.Request(
    'http://localhost:8080/api/flow/start',
    data=json.dumps({'targetUrl':'https://hmtcampus360v2.net/','dataRow':0}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST'
)
try:
    with urllib.request.urlopen(req2) as res2:
        print('STATUS', res2.status)
        print(res2.read().decode())
except urllib.error.HTTPError as e:
    print('STATUS', e.code)
    print(e.read().decode())
