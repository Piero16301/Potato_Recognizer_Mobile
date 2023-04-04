import 'package:bloc/bloc.dart';
import 'package:equatable/equatable.dart';

part 'home_state.dart';

class HomeCubit extends Cubit<HomeState> {
  HomeCubit() : super(const HomeState());

  Future<void> loadModel() async {
    emit(state.copyWith(status: ModelStatus.loading));
    try {
      await Future<void>.delayed(const Duration(seconds: 2));
      emit(state.copyWith(status: ModelStatus.success));
    } catch (e) {
      emit(state.copyWith(status: ModelStatus.failure));
    }
  }
}
