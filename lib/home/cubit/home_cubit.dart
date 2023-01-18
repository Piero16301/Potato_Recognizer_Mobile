import 'package:bloc/bloc.dart';
import 'package:equatable/equatable.dart';

part 'home_state.dart';

class HomeCubit extends Cubit<HomeState> {
  HomeCubit() : super(const HomeState());

  Future<void> initialLoading() async {
    emit(state.copyWith(status: HomeStatus.loading));
    try {
      await Future<void>.delayed(const Duration(seconds: 3));
      emit(state.copyWith(status: HomeStatus.success));
    } catch (e) {
      emit(state.copyWith(status: HomeStatus.failure));
    }
  }
}
