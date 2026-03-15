import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const AmneziaSimpleApp());
}

class AmneziaSimpleApp extends StatelessWidget {
  const AmneziaSimpleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SurfBoost VPN',
      theme: ThemeData(
        useMaterial3: true,
        primarySwatch: Colors.indigo,
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const _channel = MethodChannel('com.jacobbermudes.surfboost/vpn_channel');

  String _vpnStatus = 'Отключено';
  bool _isConnected = false;

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler(_handleNativeMethodCall);
  }

  Future<void> _handleNativeMethodCall(MethodCall call) async {
    if (call.method == 'onVpnStatusChanged') {
      final status = call.arguments as String;
      setState(() {
        _vpnStatus = status;
        _isConnected = (status == 'Подключено');
      });
    }
  }

  final String _hardcodedConfig = """
[Interface]
PrivateKey = WJ5ssSAvzTGcxXBlW0RUoszV7wcFXN8FRI3nk8oc3FY=
Address = 10.0.0.6/32
DNS = 8.8.8.8, 1.1.1.1
MTU = 1420
Jc = 5
Jmin = 8
Jmax = 80
S1 = 113
S2 = 80
S3 = 168
S4 = 1
H1 = 458630
H2 = 314753
H3 = 525401
H4 = 344614
I1 = <b 0x16030106110100060d03038c4d15e1e1ce7f045636f612b13ca8a03ddc6b6d3d938949f890c4135eb3e0b42047af4b109eafdd128e8c13b83dfc6513126d210cef4cafc91755c48be80b3be8003c130213031301c02cc030009fcca9cca8ccaac02bc02f009ec024c028006bc023c0270067c00ac0140039c009c0130033009d009c003d003c0035002f01000588ff010001000000000e000c00000979616e6465782e7275><t><r 10>

[Peer]
PublicKey = OmMmXvE172EO8d7csTQlJn6CAhfUFMYUb/0/0PlLtgA=
PresharedKey = OF1q2pntZc2C7DVF7Wnin9Gt1dOqLFt5V5XFe4IPM78=
Endpoint = 94.131.2.34:51340
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
""";

  Future<void> _toggleVpnConnection() async {
    try {
      if (_isConnected) {
        await _channel.invokeMethod('stopVpn');
      } else {
        await _channel.invokeMethod('startVpn', {'config': _hardcodedConfig});
      }
    } on PlatformException catch (e) {
      print('Ошибка MethodChannel: $e');
      setState(() {
        _vpnStatus = 'Ошибка подключения';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Amnezia Simple Connect'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            // Индикатор статуса
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
              decoration: BoxDecoration(
                color: _isConnected ? Colors.green.shade100 : Colors.red.shade100,
                borderRadius: BorderRadius.circular(20),
              ),
              child: Text(
                'Статус VPN: $_vpnStatus',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
            ),
            const SizedBox(height: 50),
            // Кнопка подключения
            ElevatedButton(
              onPressed: _toggleVpnConnection,
              style: ElevatedButton.styleFrom(
                backgroundColor: Theme.of(context).primaryColor,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 15),
              ),
              child: Text(_isConnected ? 'Отключить' : 'Подключить'),
            ),
          ],
        ),
      ),
    );
  }
}