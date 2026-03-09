# シンプルスピードメーター (Simple Speedometer)

GNSS（全球測位衛星システム）を利用した、高サンプリングレートかつ高カスタマイズ性を備えたAndroid用スピードメーターアプリケーション。Google Play Servicesに依存せず、OS標準の低レイヤーAPIを直接制御することで、低遅延かつ透明性の高い測位を実現しています。

## 1. システムアーキテクチャ (System Architecture)

### 1.1 位置測位エンジンと A-GPS 実装
- **マルチプロバイダ・ハイブリッド戦略**:
    - `GPS_PROVIDER`: 衛星信号を直接受信し、`location.speed` メタデータを主ソースとして使用。
    - `NETWORK_PROVIDER`: 基地局/Wi-Fi情報を利用。A-GPS（Assisted GPS）データとして機能し、GPSロック前のTTFF（Time To First Fix）を大幅に短縮。
- **初動高速化 (Latency Hiding)**:
    - 起動時に `getLastKnownLocation` を評価。1分以内の有効なキャッシュデータが存在する場合、即座にUIへ反映。
- **サンプリング制御**:
    - ユーザー定義の `updateInterval` (100ms ~ 3000ms) を `requestLocationUpdates` の `minTime` にバインド。`DisposableEffect` により、設定変更時のリスナー再登録（ホットリロード）を実行。

### 1.2 GNSS テレメトリ解析 (GNSS Diagnostics)
`GnssStatus.Callback` を通じて、生の衛星受信データを解析・視覚化。
- **捕捉状態の定義**:
    - `usedInFix(i)` メソッドにより、単なる信号受信ではなく、三辺測位の計算に実際に寄与している衛星数をカウント。
    - **ステータス判定**: 緑 (4基以上の `usedInFix`) / 黄 (1〜3基) / 赤 (捕捉なし) / 灰 (プロバイダ無効)。
- **信号強度 (C/N0)**:
    - `getCn0DbHz(i)` で搬送波対雑音密度を取得し、上位12基の信号強度を棒グラフ化。

## 2. 実装詳細：主要関数とロジック

### 2.1 測位・テレメトリ関連
- **`onLocationChanged(location: Location)`**:
    - 位置情報更新イベントのハンドラ。受信した `location.speed` (m/s) を km/h (×3.6) へ変換し、UI状態を更新。
- **`onSatelliteStatusChanged(status: GnssStatus)`**:
    - 衛星ステータス変更時のコールバック。全衛星の `usedInFix` 判定と `Cn0DbHz` (信号強度) の抽出を行い、`SatelliteInfo` データクラスを更新。
- **`locationManager.requestLocationUpdates(...)`**:
    - 指定された `updateInterval` をパラメータとして測位リクエストを登録。GPSとNetworkプロバイダを並列駆動させ、初期測位の確実性を担保。

### 2.2 設定データ管理 (Data Persistence)
- **`saveSettings(settings: SpeedometerSettings)`**:
    - `SharedPreferences.Editor` を使用。Compose の `Color` オブジェクトを `toArgb()` により 32bit Integer へシリアライズし、永続化層へコミット。
- **`loadSettings()`**:
    - アプリ起動時にストレージから各パラメータをデシリアライズ。欠損値がある場合はデフォルト値をフォールバックとして適用。

### 2.3 UI レンダリングとインタラクション
- **`SpeedometerApp()`**:
    - Rootコンポーザブル。`SettingsManager` による初期化と、`LaunchedEffect` によるリアクティブな自動保存（Side Effect）を管理。
- **`SpeedometerScreen()`**:
    - メインUI定義。測位状態の算出、`Box` + `offset` による重ね合わせ、および `zIndex` 指数によるスタック制御を執行。
- **`detectDragGestures` (in AdvancedColorPickerDialog)**:
    - 矩形領域内のタップ/ドラッグ座標を 0.0〜1.0 に正規化。HSVモデルの S (Saturation) と V (Value) にマッピングし、リアルタイムで色相を更新。
- **`Color.hsv(h, s, v)`**:
    - 更新されたHSV要素を Compose 用の `Color` インスタンスへ再構成。

### 2.4 ユーティリティ
- **`Color.luminance()`**:
    - W3C基準に基づき `0.2126 * R + 0.7152 * G + 0.0722 * B` で相対輝度を算出。背景色の輝度判定を行い、オーバーレイテキストのコントラスト（黒/白）を自動決定。

## 3. レイヤースタックとレイアウト (Z-Index Logic)

描画フェーズにおける `zIndex` 制御により、 Typography Overlap（フォントの重なり）を実現。
- **`zIndex(1f)`**: 速度数値（整数・小数部）。
- **`zIndex(2f)`**: 単位 (km/h)。
- **ロジック**: 単位側の `zIndex` を高く設定することで、`offset` による負のスペース調整時に、単位が数値の背後に隠れることを防ぎ、デザイン性を維持したまま極限までの接近配置を可能にする。

## 4. 使用方法 (Usage)

1. **パーミッション承認**: `ACCESS_FINE_LOCATION` の要求を許可。
2. **ステータス確認**: 左上のインジケータが緑色になることで測位の安定性を確認。
3. **カスタマイズ**:
    - 右上の設定アイコンから `ModalBottomSheet` を展開。
    - **色相**: HSVピッカーで背景・数値・単位の色を独立調整。
    - **レイアウト**: スライダーで全体のサイズ、上下左右のオフセット、数値と単位の間隔を調整。
    - **表示**: 小数点表示のON/OFFおよび、整数部に対する小数部のサイズ比率を定義。

## 5. 開発環境
- **Language**: Kotlin 2.x
- **UI Framework**: Jetpack Compose (Material 3)
- **Min SDK**: API 28 (Android 9.0)
- **Target SDK**: API 36

---
Developed as a lightweight, low-latency telemetry tool for Android.
