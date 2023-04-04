part of 'home_cubit.dart';

enum ModelStatus {
  initial,
  loading,
  success,
  failure;

  bool get isInitial => this == ModelStatus.initial;
  bool get isLoading => this == ModelStatus.loading;
  bool get isSuccess => this == ModelStatus.success;
  bool get isFailure => this == ModelStatus.failure;
}

class HomeState extends Equatable {
  const HomeState({
    this.status = ModelStatus.success,
  });

  final ModelStatus status;

  HomeState copyWith({
    ModelStatus? status,
  }) {
    return HomeState(
      status: status ?? this.status,
    );
  }

  @override
  List<Object?> get props => [
        status,
      ];
}
