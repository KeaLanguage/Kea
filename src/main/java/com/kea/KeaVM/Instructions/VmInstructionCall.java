package com.kea.KeaVM.Instructions;

import com.kea.Errors.KeaParsingError;
import com.kea.Errors.KeaRuntimeError;
import com.kea.KeaVM.Boxes.VmBaseInstructionsBox;
import com.kea.KeaVM.Builtins.VmBuiltinFunction;
import com.kea.KeaVM.Entities.VmFunction;
import com.kea.KeaVM.Entities.VmInstance;
import com.kea.KeaVM.Entities.VmUnit;
import com.kea.KeaVM.KeaVM;
import com.kea.KeaVM.VmAddress;
import com.kea.KeaVM.VmFrame;
import lombok.Getter;
import lombok.SneakyThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/*
Вызов функции в VM
 */
@SuppressWarnings("ClassCanBeRecord")
@Getter
public class VmInstructionCall implements VmInstruction {
    // адресс
    private final VmAddress addr;
    // имя
    private final String name;
    // есть ли предыдущий аксесс
    private final boolean hasPrevious;
    // аргументы
    private final VmBaseInstructionsBox args;
    // выключен ли пуш
    private final boolean shouldPushResult;

    // конструктор
    public VmInstructionCall(VmAddress addr, String name, VmBaseInstructionsBox args,
                             boolean hasPrevious, boolean shouldPushResult) {
        this.addr = addr;
        this.name = name; this.args = args; this.hasPrevious = hasPrevious;
        this.shouldPushResult = shouldPushResult;
    }

    @Override
    public void run(KeaVM vm, VmFrame<String, Object> frame)  {
        // вызов
        if (!hasPrevious) {
            callGlobalFunc(vm, frame);
        } else {
            Object last = vm.pop();
            if (last instanceof VmInstance vmInstance) {
                callInstanceFunc(vm, frame, vmInstance);
            } else if (last instanceof VmUnit vmUnit){
                callUnitFunc(vm, frame, vmUnit);
            } else {
                callReflectionFunc(vm, frame, last);
            }
        }
    }

    // Вызывает функцю объекта
    private void callInstanceFunc(KeaVM vm, VmFrame<String, Object> frame, VmInstance vmObj)  {
        // аргументы и поиск функции
        int argsAmount = passArgs(vm, frame);
        Object val = vmObj.getScope().lookup(addr, name);
        // функция
        if (val instanceof VmFunction fn) {
            checkArgs(vmObj.getType().getName() + "->" + name, fn.getArguments().size(), argsAmount);
            // вызов
            vmObj.call(addr, name, vm, shouldPushResult);
        }
        // нативная функция
        else if (val instanceof VmBuiltinFunction fn) {
            checkArgs(vmObj.getType().getName() + "->" + name, fn.args(), argsAmount);
            // вызов
            fn.exec(vm, addr);
        }
    }

    // Вызывает функцю юнита
    private void callUnitFunc(KeaVM vm, VmFrame<String, Object> frame, VmUnit vmUnit)  {
        // аргументы и поиск функции
        int argsAmount = passArgs(vm, frame);
        Object val = vmUnit.getFields().lookup(addr, name);
        // функция
        if (val instanceof VmFunction fn) {
            checkArgs(vmUnit.getName() + "->" + name, fn.getArguments().size(), argsAmount);
            // вызов
            vmUnit.call(addr, name, vm, shouldPushResult);
        }
        // нативная функция
        else if (val instanceof VmBuiltinFunction fn) {
            checkArgs(vmUnit.getName() + "->" + name, fn.args(), argsAmount);
            // вызов
            fn.exec(vm, addr);
        }
    }

    // Вызывает рефлексийную функцию
    @SneakyThrows
    private void callReflectionFunc(KeaVM vm, VmFrame<String, Object> frame, Object last)  {
        // аргументы с добавлением еденички - адресс.
        int argsAmount = passArgs(vm, frame) + 1;
        Object[] callArgs = new Object[argsAmount];
        callArgs[0] = addr;
        for (int i = argsAmount - 1; i > 0; i--) {
            callArgs[i] = vm.pop();
        }
        // рефлексийный вызов
        Method[] methods = last.getClass().getMethods();
        Method func = null;
        for (Method m : methods) {
            if (m.getName().equals(name) &&
                    m.getParameterCount() == callArgs.length) {
                func = m;
            }
        }
        if (func == null) {
            throw new KeaRuntimeError(addr.getLine(), addr.getFileName(),
                    "Jvm func not found: " + last.getClass().getName() + "->" + name,
                    "Check name for mistakes and args amount!");
        }
        else {
            checkArgs(last.getClass().getName() + "->" + name,
                    func.getParameterCount()-1, callArgs.length-1);
            try {
                // 👇 ВОЗВРАЩАЕТ NULL, ЕСЛИ ФУНКЦИЯ НИЧЕГО НЕ ВОЗВРАЩАЕТ
                Object returned = func.invoke(last, callArgs);
                if (shouldPushResult) {
                    vm.push(returned);
                }
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new KeaRuntimeError(
                        addr.getLine(), addr.getFileName(),
                        "Reflection error: " + e, "Check your code!"
                );
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof KeaRuntimeError ||
                        e.getCause() instanceof KeaParsingError) {
                    throw e.getCause();
                } else {
                    throw new KeaRuntimeError(
                            addr.getLine(), addr.getFileName(),
                            "Reflection error: " + e, "Check your code!"
                    );
                }
            }
        }
    }

    // Вызов функции из глобального скоупа
    private void callGlobalFunc(KeaVM vm, VmFrame<String, Object> frame)  {
        if (frame.has(name)) {
            // аргументы
            int argsAmount = passArgs(vm, frame);
            Object o = frame.lookup(addr, name);
            if (o instanceof VmFunction fn) {
                checkArgs(fn.getName(), fn.getArguments().size(), argsAmount);
                fn.exec(vm, shouldPushResult);
            }
            else if (o instanceof VmBuiltinFunction fn) {
                checkArgs(fn.getName(), fn.args(), argsAmount);
                fn.exec(vm, addr);
            } else {
                throw new KeaRuntimeError(addr.getLine(), addr.getFileName(),
                        "Can't call: " + o.getClass().getSimpleName(),
                        "Check your code!");
            }
        } else {
            // аргументы
            int argsAmount = passArgs(vm, frame);
            // вызов
            Object o = vm.getGlobals().lookup(addr, name);
            if (o instanceof VmFunction fn) {
                checkArgs(fn.getName(), fn.getArguments().size(), argsAmount);
                fn.exec(vm, shouldPushResult);
            }
            else if (o instanceof VmBuiltinFunction fn) {
                checkArgs(fn.getName(), fn.args(), argsAmount);
                fn.exec(vm, addr);
            } else {
                throw new KeaRuntimeError(addr.getLine(), addr.getFileName(),
                        "Can't call: " + o.getClass().getSimpleName(),
                        "Check your code!");
            }
        }
    }

    // проверка на колличество параметров и аргументов
    private void checkArgs(String name, int parameterAmount, int argsAmount) {
        if (parameterAmount != argsAmount) {
            throw new KeaRuntimeError(addr.getLine(), addr.getFileName(),
                    "Invalid args amount for call of func: "
                            + name + "(" + argsAmount + "/" + parameterAmount + ")",
                    "Check arguments amount!");
        }
    }

    // помещает аргументы в стек
    private int passArgs(KeaVM vm, VmFrame<String, Object> frame)  {
        int size = vm.getStack().size();
        for (VmInstruction instr : args.getVarContainer()) {
            instr.run(vm, frame);
        }
        return vm.getStack().size()-size;
    }

    @Override
    public String toString() {
        return "CALL_FUNCTION(" + name + ",instrs:" + args.getVarContainer().size() + ")";
    }
}
