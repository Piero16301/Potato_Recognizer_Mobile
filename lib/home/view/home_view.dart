// ignore_for_file: avoid_dynamic_calls

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_vision/flutter_vision.dart';

class HomeView extends StatelessWidget {
  const HomeView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Potato Recognizer'),
        centerTitle: true,
      ),
      body: const YoloObjectDetector(),
    );

    // return BlocBuilder<HomeCubit, HomeState>(
    //   builder: (context, state) {
    //     if (state.status.isLoading) {
    //       return const Scaffold(
    //         body: Center(
    //           child: CircularProgressIndicator(),
    //         ),
    //       );
    //     } else if (state.status.isFailure) {
    //       return const Scaffold(
    //         body: Center(
    //           child: Text('No se pudo cargar el modelo'),
    //         ),
    //       );
    //     } else if (state.status.isSuccess) {
    //       Scaffold(
    //         appBar: AppBar(
    //           title: const Text('Potato Recognizer'),
    //           centerTitle: true,
    //         ),
    //         body: const YoloObjectDetector(),
    //       );
    //     }
    //     return const SizedBox.shrink();
    //   },
    // );
  }
}

class YoloObjectDetector extends StatefulWidget {
  const YoloObjectDetector({super.key});

  @override
  State<YoloObjectDetector> createState() => _YoloObjectDetectorState();
}

class _YoloObjectDetectorState extends State<YoloObjectDetector> {
  late CameraController controller;
  late FlutterVision vision;
  late List<Map<String, dynamic>> yoloResults;
  late List<CameraDescription> cameras;

  CameraImage? cameraImage;
  bool isLoaded = false;
  bool isDetecting = false;

  @override
  void initState() {
    super.initState();

    initFunction();
  }

  Future<void> initFunction() async {
    cameras = await availableCameras();
    vision = FlutterVision();
    controller = CameraController(cameras[0], ResolutionPreset.high);
    await controller.initialize().then((value) {
      loadYoloModel().then((value) {
        setState(() {
          isLoaded = true;
          isDetecting = false;
          yoloResults = [];
        });
      });
    });
  }

  Future<void> loadYoloModel() async {
    await vision.loadYoloModel(
      labels: 'assets/potato-recognizer-labels.txt',
      modelPath: 'assets/potato-recognizer.tflite',
      modelVersion: 'yolov5',
      numThreads: 2,
      useGpu: false,
    );
    setState(() {
      isLoaded = true;
    });
  }

  @override
  Future<void> dispose() async {
    super.dispose();
    await controller.dispose();
    await vision.closeYoloModel();
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;

    if (!isLoaded) {
      return const Scaffold(
        body: Center(
          child: Text('Modelo no cargado'),
        ),
      );
    }

    return Stack(
      fit: StackFit.expand,
      children: [
        AspectRatio(
          aspectRatio: controller.value.aspectRatio,
          child: CameraPreview(
            controller,
          ),
        ),
        ...displayBoxesAroundRecognizedObjects(size),
        Positioned(
          bottom: 75,
          width: MediaQuery.of(context).size.width,
          child: Container(
            height: 80,
            width: 80,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(
                width: 5,
                color: Colors.white,
              ),
            ),
            child: isDetecting
                ? IconButton(
                    onPressed: () async {
                      await stopDetection();
                    },
                    icon: const Icon(
                      Icons.stop,
                      color: Colors.red,
                    ),
                    iconSize: 50,
                  )
                : IconButton(
                    onPressed: () async {
                      await startDetection();
                    },
                    icon: const Icon(
                      Icons.play_arrow,
                      color: Colors.white,
                    ),
                    iconSize: 50,
                  ),
          ),
        ),
      ],
    );
  }

  List<Widget> displayBoxesAroundRecognizedObjects(Size screen) {
    if (yoloResults.isEmpty) return [];
    final factorX = screen.width / (cameraImage?.height ?? 1);
    final factorY = screen.height / (cameraImage?.width ?? 1);

    const colorPick = Color.fromARGB(255, 50, 233, 30);

    return yoloResults.map((result) {
      final tag = result['tag'].toString().toUpperCase();
      final score = (result['box'][4] * 100 as double).toStringAsFixed(2);

      return Positioned(
        left: result['box'][0] * factorX as double,
        top: result['box'][1] * factorY as double,
        width: (result['box'][2] - result['box'][0]) * factorX as double,
        height: (result['box'][3] - result['box'][1]) * factorY as double,
        child: DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: const BorderRadius.all(Radius.circular(10)),
            border: Border.all(color: colorPick, width: 2),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 7.5, vertical: 7.5),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  tag,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Text(
                  '$score%',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }).toList();
  }

  Future<void> startDetection() async {
    setState(() {
      isDetecting = true;
    });
    if (controller.value.isStreamingImages) {
      return;
    }
    await controller.startImageStream((image) async {
      if (isDetecting) {
        cameraImage = image;
        await yoloOnFrame(image);
      }
    });
  }

  Future<void> yoloOnFrame(CameraImage cameraImage) async {
    final result = await vision.yoloOnFrame(
      bytesList: cameraImage.planes.map((plane) => plane.bytes).toList(),
      imageHeight: cameraImage.height,
      imageWidth: cameraImage.width,
      iouThreshold: 0.4,
      confThreshold: 0.4,
      classThreshold: 0.5,
    );
    if (result.isNotEmpty) {
      setState(() {
        yoloResults = result;
      });
    }
  }

  Future<void> stopDetection() async {
    setState(() {
      isDetecting = false;
      yoloResults.clear();
    });
  }
}
